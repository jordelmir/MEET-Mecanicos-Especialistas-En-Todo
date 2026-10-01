package com.elysium.nexus.core.hid

import com.elysium.nexus.core.model.ButtonSet
import com.elysium.nexus.core.model.CanonicalButton
import com.elysium.nexus.core.model.DpadState
import com.elysium.nexus.core.model.UniversalControllerState

/**
 * The HID report encoder.
 *
 * Converts a [UniversalControllerState] to a `ByteArray`
 * matching the [HidDescriptor.BASIC_GAMEPAD_V1_DESCRIPTOR]
 * wire format. The on-the-wire frame is:
 *
 * ```
 * byte 0:     report ID (0x01)
 * byte 1:     hat switch (low 4 bits) + 4 button bits (high 4 bits)
 * byte 2-3:   16 buttons (16 bits, packed as 2 bytes)
 * byte 4-5:   left stick X (signed 16-bit)
 * byte 6-7:   left stick Y (signed 16-bit)
 * byte 8-9:   right stick X (signed 16-bit)
 * byte 10-11: right stick Y (signed 16-bit)
 * byte 12:    left trigger (unsigned 8-bit)
 * byte 13:    right trigger (unsigned 8-bit)
 * ```
 *
 * Wait, that's 14 bytes after the report ID. The §18
 * "16 buttons, hat switch, two sticks, two triggers"
 * profile has 16 button bits = 2 bytes, plus the
 * hat switch in the high nibble of byte 1. So the
 * total report (after the report ID) is:
 *
 * ```
 * byte 0:     hat switch (low 4 bits) + 4 button bits (high 4 bits) = 1 byte
 * byte 1-2:   12 remaining button bits = 1.5 bytes, padded to 2 bytes
 * byte 3-4:   left stick X = 2 bytes
 * byte 5-6:   left stick Y = 2 bytes
 * byte 7-8:   right stick X = 2 bytes
 * byte 9-10:  right stick Y = 2 bytes
 * byte 11:    left trigger = 1 byte
 * byte 12:    right trigger = 1 byte
 * ```
 *
 * Total 13 bytes. The first byte of the report is the
 * hat switch in the low nibble; the high nibble carries
 * 4 of the 16 buttons (the rest of the buttons occupy
 * bytes 1-2). This packing matches what every
 * commercial gamepad (DualShock, DualSense, Switch Pro)
 * does for compatibility with the HID Usage Tables'
 * 16-button minimum.
 *
 * ## Why a stateless object
 *
 * The encoder is a pure function. It does not own a
 * clock, a coroutine scope, or any Android types. The
 * transport layer (Phase 2+) calls this encoder from
 * its `sendReliable(...)` and `sendRealtime(...)`
 * implementations. The tests are JVM-only and run in
 * milliseconds.
 */
object HidReportEncoder {

    /**
     * Encode a [UniversalControllerState] as a
     * `BASIC_GAMEPAD_V1` HID report.
     *
     * @param state the canonical state to encode.
     * @return a `ByteArray` of length
     *   [HidDescriptor.BASIC_GAMEPAD_V1_REPORT_SIZE]
     *   matching the wire format. Bytes 0..N-1 are the
     *   report; the caller is responsible for
     *   prepending the report ID byte when sending to
     *   the host.
     */
    fun encodeBasicGamepadV1(state: UniversalControllerState): ByteArray {
        val out = ByteArray(HidDescriptor.BASIC_GAMEPAD_V1_REPORT_SIZE)
        var idx = 0

        // ---- byte 0: hat switch (low nibble) + 4 buttons (high nibble) ----
        val hatByte: Byte = DpadState.toHatSwitch(state.dpad).toByte()
        // Buttons 0..3 go in the high nibble of byte 0. The
        // first 4 canonical buttons (South, East, West,
        // North by ordinal) occupy those bits in the order
        // they appear in the descriptor's Usage declaration.
        val nibble0: Int = if (state.buttons.isPressed(CanonicalButton.values()[0])) 0x10 else 0
        val nibble1: Int = if (state.buttons.isPressed(CanonicalButton.values()[1])) 0x20 else 0
        val nibble2: Int = if (state.buttons.isPressed(CanonicalButton.values()[2])) 0x40 else 0
        val nibble3: Int = if (state.buttons.isPressed(CanonicalButton.values()[3])) 0x80 else 0
        out[idx++] = (hatByte.toInt() or nibble0 or nibble1 or nibble2 or nibble3).toByte()

        // ---- bytes 1-2: the remaining 12 buttons (bits 4-15) ----
        // 16 buttons total; we used the first 4 in byte 0.
        // Bits 4..15 occupy bytes 1 and 2 in little-endian
        // bit order (LSB first, matching the Input(Data,
        // Var, Abs) declaration).
        var buttonWord: Int = 0
        for (i in 4 until 16) {
            if (state.buttons.isPressed(CanonicalButton.values()[i])) {
                buttonWord = buttonWord or (1 shl (i - 4))
            }
        }
        out[idx++] = (buttonWord and 0xFF).toByte()
        out[idx++] = ((buttonWord ushr 8) and 0xFF).toByte()

        // ---- bytes 3-4: left stick X (signed 16-bit) ----
        val leftX: Short = stickToInt16(state.leftStick.x)
        out[idx++] = (leftX.toInt() and 0xFF).toByte()
        out[idx++] = ((leftX.toInt() ushr 8) and 0xFF).toByte()

        // ---- bytes 5-6: left stick Y (signed 16-bit) ----
        val leftY: Short = stickToInt16(state.leftStick.y)
        out[idx++] = (leftY.toInt() and 0xFF).toByte()
        out[idx++] = ((leftY.toInt() ushr 8) and 0xFF).toByte()

        // ---- bytes 7-8: right stick X (signed 16-bit) ----
        val rightX: Short = stickToInt16(state.rightStick.x)
        out[idx++] = (rightX.toInt() and 0xFF).toByte()
        out[idx++] = ((rightX.toInt() ushr 8) and 0xFF).toByte()

        // ---- bytes 9-10: right stick Y (signed 16-bit) ----
        val rightY: Short = stickToInt16(state.rightStick.y)
        out[idx++] = (rightY.toInt() and 0xFF).toByte()
        out[idx++] = ((rightY.toInt() ushr 8) and 0xFF).toByte()

        // ---- byte 11: left trigger (unsigned 8-bit) ----
        out[idx++] = triggerToUInt8(state.leftTrigger.value)

        // ---- byte 12: right trigger (unsigned 8-bit) ----
        out[idx++] = triggerToUInt8(state.rightTrigger.value)

        return out
    }

    /**
     * Map a canonical stick value in `[-1.0, 1.0]` to a
     * signed 16-bit value in `[-32768, 32767]`. We use
     * the int16 range rather than the int8 range so the
     * stick has 16-bit resolution — better than the §18
     * minimum (int8) and a strict improvement that most
     * modern hosts accept.
     */
    private fun stickToInt16(value: Float): Short {
        val clamped = value.coerceIn(-1f, 1f)
        // Map [-1, 1] to [-32768, 32767]. We use the
        // asymmetric mapping that is conventional for HID
        // joysticks: -1.0 maps to -32768 (not -32767), and
        // +1.0 maps to +32767. The asymmetry is a quirk of
        // two's-complement int16.
        val scaled = clamped * 32767f
        return scaled.toInt()
            .coerceIn(-32768, 32767)
            .toShort()
    }

    /**
     * Map a canonical trigger value in `[0.0, 1.0]` to an
     * unsigned 8-bit value in `[0, 255]`. The §18
     * minimum is 8-bit unsigned.
     */
    private fun triggerToUInt8(value: Float): Byte {
        val clamped = value.coerceIn(0f, 1f)
        return (clamped * 255f).toInt().coerceIn(0, 255).toByte()
    }
}
