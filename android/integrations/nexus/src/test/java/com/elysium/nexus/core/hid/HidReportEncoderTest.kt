package com.elysium.nexus.core.hid

import com.elysium.nexus.core.model.CanonicalButton
import com.elysium.nexus.core.model.DpadState
import com.elysium.nexus.core.model.StickState
import com.elysium.nexus.core.model.TriggerState
import com.elysium.nexus.core.model.UniversalControllerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [HidReportEncoder] — the encoder that turns a
 * [UniversalControllerState] into a HID report.
 *
 * The encoder is a pure function. The tests assert the
 * wire format is what the host expects: hat switch in
 * the low nibble, 4 buttons in the high nibble, 12
 * buttons packed little-endian, sticks as signed 16-bit,
 * triggers as unsigned 8-bit.
 */
class HidReportEncoderTest {

    @Test
    fun neutralStateProducesAllZerosExceptHatNeutral() {
        // A neutral state should encode to:
        //   byte 0 = 0x08 (hat neutral = 8, low nibble)
        //   bytes 1-2 = 0x00 (no buttons)
        //   bytes 3-4 = 0x00 (left stick X = 0)
        //   bytes 5-6 = 0x00 (left stick Y = 0)
        //   bytes 7-8 = 0x00 (right stick X = 0)
        //   bytes 9-10 = 0x00 (right stick Y = 0)
        //   byte 11 = 0x00 (left trigger)
        //   byte 12 = 0x00 (right trigger)
        val state = UniversalControllerState.neutral()
        val out = HidReportEncoder.encodeBasicGamepadV1(state)
        assertEquals(HidDescriptor.BASIC_GAMEPAD_V1_REPORT_SIZE, out.size)
        assertEquals(0x08.toByte(), out[0]) // hat neutral
        for (i in 1 until out.size) {
            assertEquals("byte $i should be 0", 0, out[i].toInt())
        }
    }

    @Test
    fun northHatEncodesAsZero() {
        // DpadState.toHatSwitch(North) = 0
        val state = UniversalControllerState.neutral().copy(dpad = DpadState.North)
        val out = HidReportEncoder.encodeBasicGamepadV1(state)
        assertEquals(0x00.toByte(), out[0])
    }

    @Test
    fun northEastHatEncodesAsOne() {
        val state = UniversalControllerState.neutral().copy(dpad = DpadState.NorthEast)
        val out = HidReportEncoder.encodeBasicGamepadV1(state)
        assertEquals(0x01.toByte(), out[0])
    }

    @Test
    fun centerHatEncodesAsEight() {
        val state = UniversalControllerState.neutral().copy(dpad = DpadState.Center)
        val out = HidReportEncoder.encodeBasicGamepadV1(state)
        assertEquals(0x08.toByte(), out[0])
    }

    @Test
    fun fourButtonsGoInHatHighNibble() {
        // The first 4 canonical buttons (South, East,
        // West, North) go in the high nibble of byte 0
        // (bits 4, 5, 6, 7).
        val state = UniversalControllerState.neutral().copy(
            buttons = with(CanonicalButton.values()[0]) { /* noop */ com.elysium.nexus.core.model.ButtonSet.EMPTY }
                .with(CanonicalButton.values()[0], true) // South
                .with(CanonicalButton.values()[1], true) // East
                .with(CanonicalButton.values()[2], true) // West
                .with(CanonicalButton.values()[3], true) // North
        )
        val out = HidReportEncoder.encodeBasicGamepadV1(state)
        // High nibble = 0xF0 (all 4 bits set), low nibble
        // = 0x08 (hat neutral). Total = 0xF8.
        assertEquals(0xF8.toByte(), out[0])
    }

    @Test
    fun remaining12ButtonsPackAsLittleEndian() {
        // Buttons 4-15 (12 buttons) pack into bytes 1-2
        // in little-endian bit order. Pressing button 4
        // (ordinal 4, the 5th canonical button = first
        // of the "remaining" pack) sets bit 0 of byte 1.
        val state = UniversalControllerState.neutral().copy(
            buttons = com.elysium.nexus.core.model.ButtonSet.EMPTY
                .with(CanonicalButton.values()[4], true)
        )
        val out = HidReportEncoder.encodeBasicGamepadV1(state)
        // byte 0 = 0x08 (hat neutral, no buttons in high
        // nibble).
        assertEquals(0x08.toByte(), out[0])
        // byte 1 = 0x01 (bit 0 set = button 4 pressed).
        assertEquals(0x01.toByte(), out[1])
        // byte 2 = 0x00 (no buttons in the upper 8).
        assertEquals(0x00.toByte(), out[2])
    }

    @Test
    fun leftStickFullRightEncodesAsInt16Max() {
        val state = UniversalControllerState.neutral().copy(
            leftStick = StickState(1f, 0f)
        )
        val out = HidReportEncoder.encodeBasicGamepadV1(state)
        // bytes 3-4 = left stick X = 1.0 mapped to int16
        val x = (out[3].toInt() and 0xFF) or ((out[4].toInt() and 0xFF) shl 8)
        // 1.0 * 32767 = 32767, which is 0x7FFF.
        assertEquals(0x7FFF, x.toShort().toInt() and 0xFFFF)
    }

    @Test
    fun leftStickFullLeftEncodesAsInt16Min() {
        val state = UniversalControllerState.neutral().copy(
            leftStick = StickState(-1f, 0f)
        )
        val out = HidReportEncoder.encodeBasicGamepadV1(state)
        val x = (out[3].toInt() and 0xFF) or ((out[4].toInt() and 0xFF) shl 8)
        // -1.0 * 32767 = -32767, which is 0x8001.
        assertEquals(0x8001, x.toShort().toInt() and 0xFFFF)
    }

    @Test
    fun leftTriggerFullEncodesAs255() {
        val state = UniversalControllerState.neutral().copy(
            leftTrigger = TriggerState(1f)
        )
        val out = HidReportEncoder.encodeBasicGamepadV1(state)
        assertEquals(0xFF.toByte(), out[11])
    }

    @Test
    fun rightTriggerHalfEncodesAs127Or128() {
        // 0.5 * 255 = 127.5, which rounds to 127 or 128
        // depending on the encoder's rounding rules. We
        // accept either as a valid int8 representation
        // of "halfway".
        val state = UniversalControllerState.neutral().copy(
            rightTrigger = TriggerState(0.5f)
        )
        val out = HidReportEncoder.encodeBasicGamepadV1(state)
        val v = out[12].toInt() and 0xFF
        assertTrue("expected ~127, got $v", v == 127 || v == 128)
    }

    @Test
    fun allNeutralBytesAreZeroExceptHat() {
        val state = UniversalControllerState.neutral()
        val out = HidReportEncoder.encodeBasicGamepadV1(state)
        // Reset byte 0 to 0 (ignoring hat) and check
        // every other byte is 0.
        out[0] = 0
        for (i in out.indices) {
            assertEquals("byte $i should be 0", 0, out[i].toInt())
        }
    }

    @Test
    fun encodingIsDeterministic() {
        val state = UniversalControllerState.neutral().copy(
            leftStick = StickState(0.5f, 0.5f),
            leftTrigger = TriggerState(0.3f)
        )
        val a = HidReportEncoder.encodeBasicGamepadV1(state)
        val b = HidReportEncoder.encodeBasicGamepadV1(state)
        assertEquals(a.toList(), b.toList())
    }

    @Test
    fun differentStatesProduceDifferentReports() {
        val a = HidReportEncoder.encodeBasicGamepadV1(
            UniversalControllerState.neutral().copy(leftStick = StickState(0.5f, 0f))
        )
        val b = HidReportEncoder.encodeBasicGamepadV1(
            UniversalControllerState.neutral().copy(leftStick = StickState(-0.5f, 0f))
        )
        assertNotEquals(a.toList(), b.toList())
    }
}
