package com.elysium.nexus.core.transport.mac

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V0.7 Phase 32 — directional channel keys, nonce
 * domains, anti-replay and AAD tests.
 *
 * Pure JVM: X25519 + HKDF + ChaCha20-Poly1305 are
 * all available on the desktop JDK.
 */
class MacCryptoPhase32DirectionalTest {

    @Test
    fun `directional keys - alice tx equals bob rx and vice versa`() {
        val alice = MacCrypto.generateKeyPair()
        val bob = MacCrypto.generateKeyPair()

        val aliceKeys = MacCrypto.deriveChannelKeys(alice, bob.publicKeyBytes, MacCrypto.ChannelSide.PHONE)
        val bobKeys = MacCrypto.deriveChannelKeys(bob, alice.publicKeyBytes, MacCrypto.ChannelSide.MAC)

        assertArrayEquals("alice TX must equal bob RX", aliceKeys.txKeyBytes, bobKeys.rxKeyBytes)
        assertArrayEquals("alice RX must equal bob TX", aliceKeys.rxKeyBytes, bobKeys.txKeyBytes)
        assertTrue("TX and RX keys must differ on each side",
            aliceKeys.txKeyBytes.contentEquals(aliceKeys.rxKeyBytes).not())
    }

    @Test
    fun `phone to mac roundtrip works`() {
        val phone = MacCrypto.generateKeyPair()
        val mac = MacCrypto.generateKeyPair()
        val phoneKeys = MacCrypto.deriveChannelKeys(phone, mac.publicKeyBytes, MacCrypto.ChannelSide.PHONE)
        val macKeys = MacCrypto.deriveChannelKeys(mac, phone.publicKeyBytes, MacCrypto.ChannelSide.MAC)

        val frame = phoneKeys.encryptToPeer("hello from phone".toByteArray())
        assertArrayEquals("hello from phone".toByteArray(), macKeys.decryptFromPeer(frame))
    }

    @Test
    fun `mac to phone roundtrip works`() {
        val phone = MacCrypto.generateKeyPair()
        val mac = MacCrypto.generateKeyPair()
        val phoneKeys = MacCrypto.deriveChannelKeys(phone, mac.publicKeyBytes, MacCrypto.ChannelSide.PHONE)
        val macKeys = MacCrypto.deriveChannelKeys(mac, phone.publicKeyBytes, MacCrypto.ChannelSide.MAC)

        val frame = macKeys.encryptToPeer("screen frame".toByteArray())
        assertArrayEquals("screen frame".toByteArray(), phoneKeys.decryptFromPeer(frame))
    }

    @Test
    fun `nonce domain prevents a sender from replaying its own frame back to itself`() {
        val phone = MacCrypto.generateKeyPair()
        val mac = MacCrypto.generateKeyPair()
        val phoneKeys = MacCrypto.deriveChannelKeys(phone, mac.publicKeyBytes, MacCrypto.ChannelSide.PHONE)
        val macKeys = MacCrypto.deriveChannelKeys(mac, phone.publicKeyBytes, MacCrypto.ChannelSide.MAC)

        val frame = phoneKeys.encryptToPeer("command".toByteArray())
        // The phone must not be able to open its own outbound frame.
        val thrown = runCatching { phoneKeys.decryptFromPeer(frame) }.exceptionOrNull()
        assertTrue("wrong-direction frame must be rejected", thrown is MacCrypto.ReplayRejectedException)
        // And the Mac opens it fine.
        assertArrayEquals("command".toByteArray(), macKeys.decryptFromPeer(frame))
    }

    @Test
    fun `interleaved frames on the same channel stay distinct and ordered`() {
        val phone = MacCrypto.generateKeyPair()
        val mac = MacCrypto.generateKeyPair()
        val phoneKeys = MacCrypto.deriveChannelKeys(phone, mac.publicKeyBytes, MacCrypto.ChannelSide.PHONE)
        val macKeys = MacCrypto.deriveChannelKeys(mac, phone.publicKeyBytes, MacCrypto.ChannelSide.MAC)

        val f1 = phoneKeys.encryptToPeer("one".toByteArray())
        val f2 = phoneKeys.encryptToPeer("two".toByteArray())
        assertFalse("nonces must never repeat", f1.copyOfRange(0, 12).contentEquals(f2.copyOfRange(0, 12)))
        assertEquals(1L, MacCrypto.NonceCounter.sequenceOf(f1.copyOfRange(0, 12)))
        assertEquals(2L, MacCrypto.NonceCounter.sequenceOf(f2.copyOfRange(0, 12)))
        assertEquals("one", String(macKeys.decryptFromPeer(f1)))
        assertEquals("two", String(macKeys.decryptFromPeer(f2)))
    }

    @Test
    fun `replay of an already-seen frame is rejected`() {
        val phone = MacCrypto.generateKeyPair()
        val mac = MacCrypto.generateKeyPair()
        val phoneKeys = MacCrypto.deriveChannelKeys(phone, mac.publicKeyBytes, MacCrypto.ChannelSide.PHONE)
        val macKeys = MacCrypto.deriveChannelKeys(mac, phone.publicKeyBytes, MacCrypto.ChannelSide.MAC)

        val f1 = phoneKeys.encryptToPeer("one".toByteArray())
        val f2 = phoneKeys.encryptToPeer("two".toByteArray())
        macKeys.decryptFromPeer(f1)
        macKeys.decryptFromPeer(f2)
        val replayed = runCatching { macKeys.decryptFromPeer(f1) }.exceptionOrNull()
        assertTrue("duplicate sequence must be rejected", replayed is MacCrypto.ReplayRejectedException)
    }

    @Test
    fun `out-of-order frame within the window is accepted`() {
        val phone = MacCrypto.generateKeyPair()
        val mac = MacCrypto.generateKeyPair()
        val phoneKeys = MacCrypto.deriveChannelKeys(phone, mac.publicKeyBytes, MacCrypto.ChannelSide.PHONE)
        val macKeys = MacCrypto.deriveChannelKeys(mac, phone.publicKeyBytes, MacCrypto.ChannelSide.MAC)

        val f1 = phoneKeys.encryptToPeer("one".toByteArray())
        val f2 = phoneKeys.encryptToPeer("two".toByteArray())
        val f3 = phoneKeys.encryptToPeer("three".toByteArray())
        assertEquals("three", String(macKeys.decryptFromPeer(f3)))
        assertEquals("two", String(macKeys.decryptFromPeer(f2)))
        assertEquals("one", String(macKeys.decryptFromPeer(f1)))
    }

    @Test
    fun `frames older than the guard window are rejected`() {
        val phone = MacCrypto.generateKeyPair()
        val mac = MacCrypto.generateKeyPair()
        val phoneKeys = MacCrypto.deriveChannelKeys(phone, mac.publicKeyBytes, MacCrypto.ChannelSide.PHONE)
        val macKeys = MacCrypto.deriveChannelKeys(mac, phone.publicKeyBytes, MacCrypto.ChannelSide.MAC)
        val tinyGuard = MacCrypto.ChannelKeys(
            MacCrypto.ChannelSide.MAC,
            macKeys.txKeyBytes,
            macKeys.rxKeyBytes,
            MacCrypto.ReplayGuard(windowSize = 8)
        )

        val f1 = phoneKeys.encryptToPeer("one".toByteArray())
        val f2 = phoneKeys.encryptToPeer("two".toByteArray())
        val f3 = phoneKeys.encryptToPeer("three".toByteArray())
        val f4 = phoneKeys.encryptToPeer("four".toByteArray())
        val f5 = phoneKeys.encryptToPeer("five".toByteArray())
        val f6 = phoneKeys.encryptToPeer("six".toByteArray())
        val f7 = phoneKeys.encryptToPeer("seven".toByteArray())
        val f8 = phoneKeys.encryptToPeer("eight".toByteArray())
        val f9 = phoneKeys.encryptToPeer("nine".toByteArray())
        val f10 = phoneKeys.encryptToPeer("ten".toByteArray())

        for (f in listOf(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10)) {
            tinyGuard.decryptFromPeer(f)
        }
        val stale = runCatching { tinyGuard.decryptFromPeer(f2) }.exceptionOrNull()
        assertTrue("seq 2 must fall out of the 8-frame window", stale is MacCrypto.ReplayRejectedException)
    }

    @Test
    fun `channelAd AAD is bound to direction and version`() {
        val ad = MacCrypto.channelAd(MacCrypto.NonceDomain.PHONE_TO_MAC)
        assertEquals("elysium-link-v1|domain=PHONE_TO_MAC", String(ad))
        assertNotEquals(String(ad), String(MacCrypto.channelAd(MacCrypto.NonceDomain.MAC_TO_PHONE)))
        assertNotEquals(String(ad), String(MacCrypto.channelAd(MacCrypto.NonceDomain.PHONE_TO_MAC, 2)))
    }

    @Test
    fun `AAD mismatch fails authentication`() {
        val phone = MacCrypto.generateKeyPair()
        val mac = MacCrypto.generateKeyPair()
        val phoneKeys = MacCrypto.deriveChannelKeys(phone, mac.publicKeyBytes, MacCrypto.ChannelSide.PHONE)
        val macKeys = MacCrypto.deriveChannelKeys(mac, phone.publicKeyBytes, MacCrypto.ChannelSide.MAC)

        val goodAd = MacCrypto.channelAd(MacCrypto.NonceDomain.PHONE_TO_MAC)
        val badAd = MacCrypto.channelAd(MacCrypto.NonceDomain.PHONE_TO_MAC, 2)
        val frame = phoneKeys.encryptToPeer("data".toByteArray(), goodAd)
        assertArrayEquals("data".toByteArray(), macKeys.decryptFromPeer(frame, goodAd))
        val thrown = runCatching { macKeys.decryptFromPeer(frame, badAd) }.exceptionOrNull()
        assertTrue("tampered AAD must fail AEAD", thrown != null)
    }
}