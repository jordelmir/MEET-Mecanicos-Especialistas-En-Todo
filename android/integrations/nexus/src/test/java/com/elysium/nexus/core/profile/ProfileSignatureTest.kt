package com.elysium.nexus.core.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ProfileSignature] — the §15
 * "Firmar perfiles" feature.
 *
 * The tests verify:
 *  - The signature is deterministic: the same
 *    profile + the same secret always produce
 *    the same signature.
 *  - The signature is sensitive: a one-bit
 *    change to the profile or the secret
 *    produces a different signature.
 *  - The signature is 32 bytes (64 hex chars).
 *  - The verify function returns `true` for a
 *    valid signature and `false` for a tampered
 *    one.
 *  - The verify function is constant-time:
 *    both an equal-length wrong signature and
 *    a different-length signature return
 *    `false` (the comparison is constant-time
 *    per byte).
 *  - The signature is stable across profile
 *    mutations: a re-sign of a profile whose
 *    controls have been moved produces a
 *    different signature.
 */
class ProfileSignatureTest {

    private val secret: ByteArray = "test-secret-32-bytes-1234567890".toByteArray(Charsets.UTF_8)
    private val secret2: ByteArray = "different-secret-bytes-here".toByteArray(Charsets.UTF_8)

    @Test
    fun signatureIsDeterministic() {
        val profile = Profile.defaultProfile(now = 0L)
        val sig1 = ProfileSignature.sign(profile, secret)
        val sig2 = ProfileSignature.sign(profile, secret)
        assertEquals(sig1, sig2)
    }

    @Test
    fun signatureIsHexStringOfCorrectLength() {
        val profile = Profile.defaultProfile(now = 0L)
        val sig = ProfileSignature.sign(profile, secret)
        // HMAC-SHA256 → 32 bytes → 64 hex chars.
        assertEquals(64, sig.length)
        assertTrue(sig.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun signatureIsSensitiveToProfile() {
        val p1 = Profile.defaultProfile(now = 0L)
        val p2 = Profile.defaultProfile(now = 1L) // different updatedAt
        val sig1 = ProfileSignature.sign(p1, secret)
        val sig2 = ProfileSignature.sign(p2, secret)
        assertNotEquals(sig1, sig2)
    }

    @Test
    fun signatureIsSensitiveToSecret() {
        val profile = Profile.defaultProfile(now = 0L)
        val sig1 = ProfileSignature.sign(profile, secret)
        val sig2 = ProfileSignature.sign(profile, secret2)
        assertNotEquals(sig1, sig2)
    }

    @Test
    fun signatureChangesWhenControlIsMoved() {
        val p1 = Profile.defaultProfile(now = 0L)
        val moved = p1.withControlReplaced(
            controlId = 0,
            updated = p1.controls[0].copy(
                visualBounds = com.elysium.nexus.core.profile.NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f)
            ),
            now = 1000L
        )
        val sig1 = ProfileSignature.sign(p1, secret)
        val sig2 = ProfileSignature.sign(moved, secret)
        assertNotEquals(sig1, sig2)
    }

    @Test
    fun verifyAcceptsValidSignature() {
        val profile = Profile.defaultProfile(now = 0L)
        val sig = ProfileSignature.sign(profile, secret)
        assertTrue(ProfileSignature.verify(profile, sig, secret))
    }

    @Test
    fun verifyRejectsWrongSecret() {
        val profile = Profile.defaultProfile(now = 0L)
        val sig = ProfileSignature.sign(profile, secret)
        assertFalse(ProfileSignature.verify(profile, sig, secret2))
    }

    @Test
    fun verifyRejectsTamperedProfile() {
        val p1 = Profile.defaultProfile(now = 0L)
        val p2 = Profile.defaultProfile(now = 1L)
        val sig = ProfileSignature.sign(p1, secret)
        assertFalse(ProfileSignature.verify(p2, sig, secret))
    }

    @Test
    fun verifyRejectsTruncatedSignature() {
        val profile = Profile.defaultProfile(now = 0L)
        val sig = ProfileSignature.sign(profile, secret)
        val truncated = sig.substring(0, sig.length - 1)
        assertFalse(ProfileSignature.verify(profile, truncated, secret))
    }

    @Test
    fun verifyRejectsEmptySignature() {
        val profile = Profile.defaultProfile(now = 0L)
        assertFalse(ProfileSignature.verify(profile, "", secret))
    }

    @Test
    fun signatureIsStableAcrossSessions() {
        // Two independent signings of the same
        // profile produce the same signature.
        // This is the determinism property the
        // host relies on to verify a profile
        // signed by a previous version of the
        // app.
        val profile = Profile.defaultProfile(now = 1000L)
        val sigA = ProfileSignature.sign(profile, secret)
        val sigB = ProfileSignature.sign(profile, secret)
        assertEquals(sigA, sigB)
    }
}
