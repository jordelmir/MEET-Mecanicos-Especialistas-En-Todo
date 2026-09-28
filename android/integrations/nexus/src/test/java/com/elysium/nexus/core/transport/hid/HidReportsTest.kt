package com.elysium.nexus.core.transport.hid

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the HID report builders.
 *
 * The builders are pure functions over bytes —
 * the unit tests verify the byte layout against
 * the USB HID 1.11 specification (boot keyboard
 * + mouse + consumer control). The Mac side
 * uses CryptoKit's `ChaChaPoly` and the Swift
 * `HIDEvent` builder; both produce identical
 * bytes when the descriptors match.
 */
class HidReportsTest {

    // === Keyboard reports ===

    @Test
    fun `empty keyboard report is 8 bytes of zero`() {
        val r = HidReports.keyboardReleaseAll()
        assertEquals(8, r.size)
        for (b in r) {
            assertEquals(0, b.toInt() and 0xFF)
        }
    }

    @Test
    fun `keyboard report with no keys has only the modifier byte set`() {
        val r = HidReports.keyboard(modifier = 0)
        assertEquals(8, r.size)
        assertEquals(0, r[0].toInt() and 0xFF)
        for (i in 1..7) {
            assertEquals(0, r[i].toInt() and 0xFF)
        }
    }

    @Test
    fun `keyboard report with left shift modifier`() {
        val r = HidReports.keyboard(
            modifier = HidDescriptors.Modifier.LEFT_SHIFT,
            keycodes = intArrayOf(0x04) // a
        )
        assertEquals(8, r.size)
        assertEquals(HidDescriptors.Modifier.LEFT_SHIFT, r[0].toInt() and 0xFF)
        assertEquals(0, r[1].toInt() and 0xFF)
        assertEquals(0x04, r[2].toInt() and 0xFF)
    }

    @Test
    fun `keyboard report with multiple keys`() {
        val r = HidReports.keyboard(
            modifier = HidDescriptors.Modifier.LEFT_CTRL,
            keycodes = intArrayOf(0x04, 0x05, 0x06) // a, b, c
        )
        assertEquals(8, r.size)
        assertEquals(HidDescriptors.Modifier.LEFT_CTRL, r[0].toInt() and 0xFF)
        assertEquals(0x04, r[2].toInt() and 0xFF)
        assertEquals(0x05, r[3].toInt() and 0xFF)
        assertEquals(0x06, r[4].toInt() and 0xFF)
    }

    @Test
    fun `keyboard report caps at 6 simultaneous keys`() {
        val r = HidReports.keyboard(
            modifier = 0,
            keycodes = intArrayOf(0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A)
        )
        // 6 keys are emitted; the 7th is ignored.
        for (i in 2..7) {
            assertEquals("key at index $i should be non-zero", 0x04 + (i - 2), r[i].toInt() and 0xFF)
        }
    }

    @Test
    fun `keyboard report combines multiple modifiers`() {
        val mod = HidDescriptors.Modifier.LEFT_SHIFT or HidDescriptors.Modifier.LEFT_CTRL
        val r = HidReports.keyboard(modifier = mod, keycodes = intArrayOf(0x04))
        assertEquals(mod, r[0].toInt() and 0xFF)
    }

    // === Mouse reports ===

    @Test
    fun `empty mouse report is 4 bytes of zero`() {
        val r = HidReports.mouse(0, 0, 0, 0)
        assertEquals(4, r.size)
        for (b in r) {
            assertEquals(0, b.toInt() and 0xFF)
        }
    }

    @Test
    fun `mouse report with left button and no movement`() {
        val r = HidReports.mouse(HidDescriptors.MouseButton.LEFT, 0, 0, 0)
        assertEquals(4, r.size)
        assertEquals(HidDescriptors.MouseButton.LEFT, r[0].toInt() and 0xFF)
        assertEquals(0, r[1].toInt() and 0xFF)
        assertEquals(0, r[2].toInt() and 0xFF)
        assertEquals(0, r[3].toInt() and 0xFF)
    }

    @Test
    fun `mouse report with right click`() {
        val r = HidReports.mouse(HidDescriptors.MouseButton.RIGHT, 5, -3, 0)
        assertEquals(HidDescriptors.MouseButton.RIGHT, r[0].toInt() and 0xFF)
        assertEquals(5, r[1].toInt())
        assertEquals(-3, r[2].toInt())
    }

    @Test
    fun `mouse report clips X to byte range`() {
        val r = HidReports.mouse(0, 200, 0, 0)
        assertEquals(127, r[1].toInt()) // 200 clips to 127
    }

    @Test
    fun `mouse report clips negative X to -127`() {
        val r = HidReports.mouse(0, -200, 0, 0)
        assertEquals(-127, r[1].toInt()) // -200 clips to -127
    }

    @Test
    fun `mouse report with all three buttons`() {
        val buttons = HidDescriptors.MouseButton.LEFT or
            HidDescriptors.MouseButton.RIGHT or
            HidDescriptors.MouseButton.MIDDLE
        val r = HidReports.mouse(buttons, 0, 0, 0)
        assertEquals(0x07, r[0].toInt() and 0xFF)
    }

    // === Consumer control reports ===

    @Test
    fun `consumer report encodes a 16-bit usage ID`() {
        val r = HidReports.consumer(HidDescriptors.Consumer.VOLUME_UP)
        assertEquals(2, r.size)
        val lo = r[0].toInt() and 0xFF
        val hi = r[1].toInt() and 0xFF
        val usage = (hi shl 8) or lo
        assertEquals(HidDescriptors.Consumer.VOLUME_UP, usage)
    }

    @Test
    fun `consumer report for play-pause`() {
        val r = HidReports.consumer(HidDescriptors.Consumer.PLAY_PAUSE)
        val usage = (r[1].toInt() shl 8) or (r[0].toInt() and 0xFF)
        assertEquals(0x00CD, usage)
    }

    @Test
    fun `consumer report with zero usage is 2 bytes of zero`() {
        val r = HidReports.consumer(0)
        assertEquals(2, r.size)
        assertEquals(0, r[0].toInt() and 0xFF)
        assertEquals(0, r[1].toInt() and 0xFF)
    }

    // === Modifier bitmask ===

    @Test
    fun `modifier bit positions match the macOS CGEventFlags layout`() {
        // These bit positions are pinned to the
        // macOS CGEventFlags layout (and the HID
        // modifier byte). Do not renumber them
        // or the keyboard shortcuts break.
        assertEquals(1 shl 0, HidDescriptors.Modifier.LEFT_CTRL)
        assertEquals(1 shl 1, HidDescriptors.Modifier.LEFT_SHIFT)
        assertEquals(1 shl 2, HidDescriptors.Modifier.LEFT_ALT)
        assertEquals(1 shl 3, HidDescriptors.Modifier.LEFT_GUI)
        assertEquals(1 shl 4, HidDescriptors.Modifier.RIGHT_CTRL)
        assertEquals(1 shl 5, HidDescriptors.Modifier.RIGHT_SHIFT)
        assertEquals(1 shl 6, HidDescriptors.Modifier.RIGHT_ALT)
        assertEquals(1 shl 7, HidDescriptors.Modifier.RIGHT_GUI)
    }

    // === Consumer IDs ===

    @Test
    fun `consumer usage IDs match the USB HID Consumer Page`() {
        // These IDs are the standard USB HID
        // Consumer Page (0x0C) usage codes. Do
        // not renumber them.
        assertEquals(0x00E9, HidDescriptors.Consumer.VOLUME_UP)
        assertEquals(0x00EA, HidDescriptors.Consumer.VOLUME_DOWN)
        assertEquals(0x00E2, HidDescriptors.Consumer.VOLUME_MUTE)
        assertEquals(0x00CD, HidDescriptors.Consumer.PLAY_PAUSE)
        assertEquals(0x00B5, HidDescriptors.Consumer.SCAN_NEXT)
        assertEquals(0x00B6, HidDescriptors.Consumer.SCAN_PREVIOUS)
    }

    // === Descriptor ===

    @Test
    fun `descriptor starts with the generic desktop usage page`() {
        // 0x05 0x01 = USAGE_PAGE (Generic Desktop)
        // This is the magic byte every USB HID
        // descriptor starts with.
        assertEquals(0x05, HidDescriptors.COMBO[0].toInt() and 0xFF)
        assertEquals(0x01, HidDescriptors.COMBO[1].toInt() and 0xFF)
    }

    @Test
    fun `descriptor contains the keyboard report id`() {
        // Report ID 0x01 (keyboard) — 0x85 0x01
        val idx = indexOfSequence(HidDescriptors.COMBO, byteArrayOf(0x85.toByte(), 0x01))
        assertTrue("Descriptor must contain keyboard report ID 0x01", idx >= 0)
    }

    @Test
    fun `descriptor contains the mouse report id`() {
        // Report ID 0x02 (mouse) — 0x85 0x02
        val idx = indexOfSequence(HidDescriptors.COMBO, byteArrayOf(0x85.toByte(), 0x02))
        assertTrue("Descriptor must contain mouse report ID 0x02", idx >= 0)
    }

    @Test
    fun `descriptor contains the consumer report id`() {
        // Report ID 0x03 (consumer) — 0x85 0x03
        val idx = indexOfSequence(HidDescriptors.COMBO, byteArrayOf(0x85.toByte(), 0x03))
        assertTrue("Descriptor must contain consumer report ID 0x03", idx >= 0)
    }

    @Test
    fun `descriptor ends with an end-collection`() {
        // 0xC0 = END_COLLECTION. The last byte
        // should be an end-collection that
        // closes the consumer application
        // collection.
        assertEquals(0xC0.toByte(), HidDescriptors.COMBO[HidDescriptors.COMBO.size - 1])
    }

    // === Helpers ===

    private fun indexOfSequence(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    @Test
    fun `descriptor is reasonably sized`() {
        // The descriptor is small. It must be at
        // least 64 bytes (a real combo device) and
        // not absurdly large (< 256 bytes).
        assertTrue("descriptor too small", HidDescriptors.COMBO.size > 64)
        assertTrue("descriptor too large", HidDescriptors.COMBO.size < 256)
    }

    @Test
    fun `keyboard and mouse report sizes are pinned`() {
        assertEquals(8, HidDescriptors.KEYBOARD_REPORT_SIZE)
        assertEquals(4, HidDescriptors.MOUSE_REPORT_SIZE)
    }

    @Test
    fun `mouse report button bitmask is correct`() {
        assertEquals(1 shl 0, HidDescriptors.MouseButton.LEFT)
        assertEquals(1 shl 1, HidDescriptors.MouseButton.RIGHT)
        assertEquals(1 shl 2, HidDescriptors.MouseButton.MIDDLE)
    }
}
