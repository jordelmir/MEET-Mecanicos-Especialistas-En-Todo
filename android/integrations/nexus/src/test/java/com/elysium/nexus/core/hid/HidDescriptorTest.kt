package com.elysium.nexus.core.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [HidDescriptor] — the BASIC_GAMEPAD_V1
 * descriptor bytes.
 *
 * The descriptor is a hand-written byte array. The
 * test pins the byte count and a few key structural
 * properties (the start of the descriptor tree, the
 * report size, the device name) so a future
 * contributor cannot silently change the descriptor
 * without breaking the test.
 */
class HidDescriptorTest {

    @Test
    fun basicGamepadV1IsNonEmpty() {
        assertTrue(
            "descriptor must be non-empty",
            HidDescriptor.BASIC_GAMEPAD_V1_DESCRIPTOR.isNotEmpty()
        )
    }

    @Test
    fun basicGamepadV1StartsWithUsagePageGamepad() {
        // First two bytes must be USAGE_PAGE(Generic
        // Desktop) = 0x05 0x01.
        assertEquals(0x05.toByte(), HidDescriptor.BASIC_GAMEPAD_V1_DESCRIPTOR[0])
        assertEquals(0x01.toByte(), HidDescriptor.BASIC_GAMEPAD_V1_DESCRIPTOR[1])
    }

    @Test
    fun basicGamepadV1DeclaresGamepadUsage() {
        // USAGE(Gamepad) = 0x09 0x05 must appear after the
        // first USAGE_PAGE.
        val bytes = HidDescriptor.BASIC_GAMEPAD_V1_DESCRIPTOR
        // Find the first 0x09 occurrence.
        val usageIndex = bytes.indexOf(0x09.toByte())
        assertTrue("expected USAGE tag, got $usageIndex", usageIndex >= 0)
        assertEquals(0x05.toByte(), bytes[usageIndex + 1])
    }

    @Test
    fun basicGamepadV1EndsWithEndCollection() {
        // The descriptor must end with END_COLLECTION
        // (0xC0) for the Application collection.
        val last = HidDescriptor.BASIC_GAMEPAD_V1_DESCRIPTOR.size - 1
        assertEquals(0xC0.toByte(), HidDescriptor.BASIC_GAMEPAD_V1_DESCRIPTOR[last])
    }

    @Test
    fun reportSizeIs13Bytes() {
        // Per the KDoc: 1 (hat) + 2 (buttons) + 4×2
        // (sticks) + 2 (triggers) = 13 bytes.
        assertEquals(13, HidDescriptor.BASIC_GAMEPAD_V1_REPORT_SIZE)
    }

    @Test
    fun reportIdIsOne() {
        // The report ID is 0x01. Future descriptor
        // variants (EXTENDED_GAMEPAD_V1, etc.) will use
        // 0x02, 0x03, etc.
        assertEquals(0x01.toByte(), HidDescriptor.BASIC_GAMEPAD_V1_REPORT_ID)
    }

    @Test
    fun deviceNameIsElysiumNexusGamepad() {
        // The §18 identity: "Elysium Nexus Gamepad".
        // Not "DualShock", not "Xbox Controller", not
        // "Joy-Con" — per §2 we do not impersonate.
        assertEquals("Elysium Nexus Gamepad", HidDescriptor.DEVICE_NAME)
    }
}
