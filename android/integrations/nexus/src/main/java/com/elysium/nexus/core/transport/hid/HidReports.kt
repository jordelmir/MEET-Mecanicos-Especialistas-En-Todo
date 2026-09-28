package com.elysium.nexus.core.transport.hid

/**
 * Builders for the three HID reports the
 * Elysium Nexus universal transport emits.
 *
 * The reports follow the USB HID 1.11 boot
 * keyboard + mouse + consumer control
 * conventions, encoded as a single byte
 * array ready for `BluetoothHidDevice.sendReport`.
 *
 * All builders are pure functions over bytes;
 * they have no Android dependencies and are
 * JVM-testable.
 */
object HidReports {

    /**
     * Build a keyboard report.
     *
     * @param modifier the bitmask from [HidDescriptors.Modifier]
     * @param keycodes up to 6 USB HID keyboard usage codes
     *                (e.g. 0x04 = 'a', 0x28 = Enter)
     * @return an 8-byte report ready to send
     */
    fun keyboard(
        modifier: Int,
        vararg keycodes: Int
    ): ByteArray {
        val out = ByteArray(HidDescriptors.KEYBOARD_REPORT_SIZE)
        out[0] = (modifier and 0xFF).toByte()
        out[1] = 0 // reserved
        var i = 0
        for (k in keycodes) {
            if (i >= 6) break
            out[2 + i] = (k and 0xFF).toByte()
            i++
        }
        return out
    }

    /**
     * Build a "no keys" keyboard report (all
     * keys released). Use this after a key-down
     * to release the key; most hosts require
     * the matching up event.
     */
    fun keyboardReleaseAll(): ByteArray = ByteArray(HidDescriptors.KEYBOARD_REPORT_SIZE)

    /**
     * Build a mouse report.
     *
     * @param buttons bitmask from [HidDescriptors.MouseButton]
     * @param dx X delta in pixels (clipped to -127..127)
     * @param dy Y delta in pixels (clipped to -127..127)
     * @param wheel wheel delta (clipped to -127..127)
     * @return a 4-byte report ready to send
     */
    fun mouse(
        buttons: Int,
        dx: Int,
        dy: Int,
        wheel: Int = 0
    ): ByteArray {
        val out = ByteArray(HidDescriptors.MOUSE_REPORT_SIZE)
        out[0] = (buttons and 0xFF).toByte()
        out[1] = clip(dx).toByte()
        out[2] = clip(dy).toByte()
        out[3] = clip(wheel).toByte()
        return out
    }

    /**
     * Build a consumer control report. A
     * non-zero usage activates the key; 0
     * releases it.
     */
    fun consumer(usageId: Int): ByteArray {
        return byteArrayOf(
            (usageId and 0xFF).toByte(),
            ((usageId ushr 8) and 0xFF).toByte()
        )
    }

    private fun clip(v: Int): Int = when {
        v < -127 -> -127
        v > 127 -> 127
        else -> v
    }
}
