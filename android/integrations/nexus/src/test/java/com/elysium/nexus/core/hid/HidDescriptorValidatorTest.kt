package com.elysium.nexus.core.hid

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [HidDescriptorValidator] — the structural
 * validation of the BASIC_GAMEPAD_V1 descriptor.
 *
 * The validator's job is to fail loudly when a future
 * contributor silently breaks the descriptor. The
 * happy path is the BASIC_GAMEPAD_V1 we shipped in
 * 0.9; the failure paths are the structural issues we
 * want to catch.
 */
class HidDescriptorValidatorTest {

    @Test
    fun baselineDescriptorIsValid() {
        val r = HidDescriptorValidator.validateBasicGamepadV1()
        assertTrue("expected Valid, got $r", r is HidDescriptorValidator.Result.Valid)
        val valid = r as HidDescriptorValidator.Result.Valid
        assertTrue("descriptor must be non-empty", valid.descriptorSizeBytes > 0)
    }

    @Test
    fun emptyBytesAreRejected() {
        val r = HidDescriptorValidator.validate(ByteArray(0))
        assertTrue("expected Invalid, got $r", r is HidDescriptorValidator.Result.Invalid)
    }

    @Test
    fun bytesWithoutUsagePageAreRejected() {
        val bytes = byteArrayOf(0x09, 0x05) // USAGE(Gamepad) without preceding USAGE_PAGE
        val r = HidDescriptorValidator.validate(bytes)
        assertTrue(r is HidDescriptorValidator.Result.Invalid)
    }

    @Test
    fun imbalancedCollectionsAreRejected() {
        val bytes = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // USAGE_PAGE(Generic Desktop)
            0x09.toByte(), 0x05.toByte(), // USAGE(Gamepad)
            0xA1.toByte(), 0x01.toByte()  // COLLECTION(Application) — no END_COLLECTION
        )
        val r = HidDescriptorValidator.validate(bytes)
        assertTrue(r is HidDescriptorValidator.Result.Invalid)
    }

    @Test
    fun descriptorWithoutInputIsRejected() {
        val bytes = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // USAGE_PAGE
            0x09.toByte(), 0x05.toByte(), // USAGE
            0xA1.toByte(), 0x01.toByte(), // COLLECTION(Application)
            0xC0.toByte()                  // END_COLLECTION
        )
        val r = HidDescriptorValidator.validate(bytes)
        assertTrue(r is HidDescriptorValidator.Result.Invalid)
    }
}
