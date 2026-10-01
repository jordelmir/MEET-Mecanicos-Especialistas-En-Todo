package com.elysium.nexus.core.transport.hid

/**
 * Elysium Nexus — HID report descriptors.
 *
 * The Bluetooth HID Device profile (API 28+)
 * requires the app to register a HID report
 * descriptor. The descriptor is a binary blob
 * that tells the host what kind of input
 * device this is, what report IDs exist, and
 * the format of every report.
 *
 * We present the phone as a **combo device**
 * (subclass `SUBCLASS1_COMBO`) that exposes
 * three top-level collections:
 *
 *  1. **Keyboard** (report ID `0x01`) — 8-byte
 *     reports: 1 modifier byte, 1 reserved,
 *     6 simultaneous keycodes. This is the
 *     standard USB HID boot keyboard report.
 *  2. **Mouse** (report ID `0x02`) — 4-byte
 *     reports: 1 buttons byte, 1 X, 1 Y, 1
 *     wheel. 8-bit signed deltas per axis.
 *     The host treats this as a generic mouse
 *     and merges it with the keyboard for the
 *     cursor + clicks story.
 *  3. **Consumer Control** (report ID `0x03`)
 *     — 2-byte reports with a 16-bit usage ID
 *     for media keys (volume, play/pause, next,
 *     previous, etc.). Most modern OSes
 *     recognise this without any extra drivers.
 *
 * The descriptor is small (~110 bytes) and
 * contains no application-specific data. It is
 * public domain — based on the USB HID 1.11
 * specification and the Boot Keyboard + Mouse
 * + Consumer Control examples from the USB
 * Implementers Forum.
 */
object HidDescriptors {

    /** The combo descriptor. */
    val COMBO: ByteArray = intArrayOf(
        // === Keyboard (Report ID 0x01) ===
        0x05, 0x01,                       // USAGE_PAGE (Generic Desktop)
        0x09, 0x06,                       // USAGE (Keyboard)
        0xA1, 0x01,                       // COLLECTION (Application)
        0x85, 0x01,                       //   REPORT_ID (1)
        0x05, 0x07,                       //   USAGE_PAGE (Keyboard)
        0x19, 0xE0,                       //   USAGE_MINIMUM (Keyboard LeftControl)
        0x29, 0xE7,                       //   USAGE_MAXIMUM (Keyboard Right GUI)
        0x15, 0x00,                       //   LOGICAL_MINIMUM (0)
        0x25, 0x01,                       //   LOGICAL_MAXIMUM (1)
        0x75, 0x01,                       //   REPORT_SIZE (1)
        0x95, 0x08,                       //   REPORT_COUNT (8) — modifier bits
        0x81, 0x02,                       //   INPUT (Data,Var,Abs) — modifier byte
        0x95, 0x01,                       //   REPORT_COUNT (1)
        0x75, 0x08,                       //   REPORT_SIZE (8) — reserved byte
        0x81, 0x01,                       //   INPUT (Constant) — reserved
        0x95, 0x05,                       //   REPORT_COUNT (5) — 5 LEDs (unused)
        0x75, 0x01,                       //   REPORT_SIZE (1)
        0x05, 0x08,                       //   USAGE_PAGE (LEDs)
        0x19, 0x01,                       //   USAGE_MINIMUM (Num Lock)
        0x29, 0x05,                       //   USAGE_MAXIMUM (Kana)
        0x91, 0x02,                       //   OUTPUT (Data,Var,Abs) — LED report
        0x95, 0x01,                       //   REPORT_COUNT (1)
        0x75, 0x03,                       //   REPORT_SIZE (3) — padding
        0x91, 0x01,                       //   OUTPUT (Constant) — padding
        0x95, 0x06,                       //   REPORT_COUNT (6) — up to 6 keys
        0x75, 0x08,                       //   REPORT_SIZE (8)
        0x15, 0x00,                       //   LOGICAL_MINIMUM (0)
        0x26, 0xFF, 0x00,                 // LOGICAL_MAXIMUM (255)
        0x05, 0x07,                       //   USAGE_PAGE (Keyboard)
        0x19, 0x00,                       //   USAGE_MINIMUM (Reserved)
        0x2A, 0xFF, 0x00,                 // USAGE_MAXIMUM (Keyboard Application)
        0x81, 0x00,                       //   INPUT (Data,Array) — key array
        0xC0,                             // END_COLLECTION

        // === Mouse (Report ID 0x02) ===
        0x05, 0x01,                       // USAGE_PAGE (Generic Desktop)
        0x09, 0x02,                       // USAGE (Mouse)
        0xA1, 0x01,                       // COLLECTION (Application)
        0x09, 0x01,                       //   USAGE (Pointer)
        0xA1, 0x00,                       //   COLLECTION (Physical)
        0x85, 0x02,                       //     REPORT_ID (2)
        0x05, 0x09,                       //     USAGE_PAGE (Buttons)
        0x19, 0x01,                       //     USAGE_MINIMUM (Button 1)
        0x29, 0x03,                       //     USAGE_MAXIMUM (Button 3)
        0x15, 0x00,                       //     LOGICAL_MINIMUM (0)
        0x25, 0x01,                       //     LOGICAL_MAXIMUM (1)
        0x95, 0x03,                       //     REPORT_COUNT (3)
        0x75, 0x01,                       //     REPORT_SIZE (1)
        0x81, 0x02,                       //     INPUT (Data,Var,Abs)
        0x95, 0x01,                       //     REPORT_COUNT (1)
        0x75, 0x05,                       //     REPORT_SIZE (5) — padding
        0x81, 0x01,                       //     INPUT (Constant) — padding
        0x05, 0x01,                       //     USAGE_PAGE (Generic Desktop)
        0x09, 0x30,                       //     USAGE (X)
        0x09, 0x31,                       //     USAGE (Y)
        0x09, 0x38,                       //     USAGE (Wheel)
        0x15, 0x81,                       //     LOGICAL_MINIMUM (-127)
        0x25, 0x7F,                       //     LOGICAL_MAXIMUM (127)
        0x75, 0x08,                       //     REPORT_SIZE (8)
        0x95, 0x03,                       //     REPORT_COUNT (3)
        0x81, 0x06,                       //     INPUT (Data,Var,Rel) — relative
        0xC0,                             //   END_COLLECTION (Physical)
        0xC0,                             // END_COLLECTION (Application)

        // === Consumer Control (Report ID 0x03) ===
        0x05, 0x0C,                       // USAGE_PAGE (Consumer Devices)
        0x09, 0x01,                       // USAGE (Consumer Control)
        0xA1, 0x01,                       // COLLECTION (Application)
        0x85, 0x03,                       //   REPORT_ID (3)
        0x15, 0x00,                       //   LOGICAL_MINIMUM (0)
        0x26, 0xFF, 0x03,                 // LOGICAL_MAXIMUM (0x03FF)
        0x19, 0x00,                       //   USAGE_MINIMUM (Unassigned)
        0x2A, 0xFF, 0x03,                 // USAGE_MAXIMUM (0x03FF)
        0x95, 0x01,                       //   REPORT_COUNT (1)
        0x75, 0x10,                       //   REPORT_SIZE (16)
        0x81, 0x00,                       //   INPUT (Data,Array)
        0xC0                              // END_COLLECTION
    ).map { it.toByte() }.toByteArray()

    /** Report IDs. */
    const val REPORT_ID_KEYBOARD: Int = 0x01
    const val REPORT_ID_MOUSE: Int = 0x02
    const val REPORT_ID_CONSUMER: Int = 0x03

    /**
     * The keyboard report format. 8 bytes:
     *  - byte 0: modifier bitmask (Ctrl, Shift, Alt, GUI)
     *  - byte 1: reserved (0)
     *  - bytes 2-7: up to 6 simultaneous keycodes
     */
    const val KEYBOARD_REPORT_SIZE: Int = 8

    /** Modifier bitmask for the keyboard report. */
    object Modifier {
        const val LEFT_CTRL: Int = 1 shl 0
        const val LEFT_SHIFT: Int = 1 shl 1
        const val LEFT_ALT: Int = 1 shl 2
        const val LEFT_GUI: Int = 1 shl 3
        const val RIGHT_CTRL: Int = 1 shl 4
        const val RIGHT_SHIFT: Int = 1 shl 5
        const val RIGHT_ALT: Int = 1 shl 6
        const val RIGHT_GUI: Int = 1 shl 7
    }

    /**
     * The mouse report format. 4 bytes:
     *  - byte 0: buttons (bit 0: left, bit 1: right, bit 2: middle)
     *  - byte 1: X delta (signed -127..127)
     *  - byte 2: Y delta (signed -127..127)
     *  - byte 3: wheel delta (signed -127..127)
     */
    const val MOUSE_REPORT_SIZE: Int = 4

    /** Mouse button bitmask. */
    object MouseButton {
        const val LEFT: Int = 1 shl 0
        const val RIGHT: Int = 1 shl 1
        const val MIDDLE: Int = 1 shl 2
    }

    /** Consumer Control usage IDs. */
    object Consumer {
        const val VOLUME_UP: Int = 0x00E9
        const val VOLUME_DOWN: Int = 0x00EA
        const val VOLUME_MUTE: Int = 0x00E2
        const val PLAY_PAUSE: Int = 0x00CD
        const val SCAN_NEXT: Int = 0x00B5
        const val SCAN_PREVIOUS: Int = 0x00B6
        const val STOP: Int = 0x00B7
        const val HOME: Int = 0x0223
        const val BRIGHTNESS_UP: Int = 0x006F
        const val BRIGHTNESS_DOWN: Int = 0x0070
    }
}
