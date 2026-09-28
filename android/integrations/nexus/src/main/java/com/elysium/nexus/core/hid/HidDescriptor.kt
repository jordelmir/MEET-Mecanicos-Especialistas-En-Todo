package com.elysium.nexus.core.hid

/**
 * The "Elysium Nexus Gamepad" HID descriptor family.
 *
 * Per `MASTER_ORDER.md` §18, the platform creates its own
 * HID descriptor under its own identity (no commercial
 * impersonation). The descriptor is shipped as a fixed
 * byte array so:
 *
 *  1. The bytes are reproducible across builds (reproducible
 *     builds per §41).
 *  2. The descriptor can be unit-tested from the JVM
 *     (parse the bytes, assert the structure).
 *  3. The descriptor is portable: the same bytes are used
 *     on the Android `BluetoothHidDevice` API (Phase 2+)
 *     and on the future Nexus Receiver (Phase 4+).
 *
 * The descriptor family has five members per §18:
 *
 * ```
 * BASIC_GAMEPAD_V1
 * EXTENDED_GAMEPAD_V1
 * KEYBOARD_MOUSE_V1
 * COMPOSITE_INPUT_V1
 * ACCESSIBILITY_CONTROLLER_V1
 * ```
 *
 * Phase 0.9 ships `BASIC_GAMEPAD_V1` only. The other four
 * are stubs that document the planned byte counts and
 * are added in 1.x+ as the surface widens.
 *
 * ## Why a hand-written byte array
 *
 * A descriptor is a TLV stream of `bTag`, `bSize`,
 * `data...` items. Generating it from a Kotlin data
 * structure would be cleaner code but a hand-written
 * byte array is:
 *
 *  - **Smaller.** A code generator would need a runtime
 *    encoder + a runtime decoder. The bytes are the
 *    truth.
 *  - **More verifiable.** The test can assert the
 *    bytes directly.
 *  - **The same format the platform will ship.** The
 *    `BluetoothHidDevice` API takes a `ByteArray`; the
 *    Nexus Receiver's HID report generator takes a
 *    `ByteArray`. We never re-encode in production.
 *
 * The §18 profile is "16 o 32 botones, hat switch, dos
 * sticks, dos gatillos, Report ID, feature report de
 * capacidades, output report propio opcional". The
 * `BASIC_GAMEPAD_V1` descriptor ships:
 *
 *  - 16 buttons (the §18 minimum).
 *  - 1 hat switch (8 directions + neutral).
 *  - 2 analog sticks (X, Y, each 8-bit signed).
 *  - 2 analog triggers (8-bit unsigned).
 *  - 1 report ID (`0x01`).
 *  - No feature report yet (lands in 1.x when the
 *    capability negotiation uses it).
 *  - No output report yet (lands in 1.x when the
 *    host-to-device haptic feedback is wired).
 *
 * The total report size is 12 bytes: 1 (report ID) + 1
 * (hat) + 2 (buttons) + 8 (4 axes × 2 bytes) = 12.
 */
object HidDescriptor {

    /**
     * Helper: build a single HID tag byte. Most
     * descriptor constants are single bytes, but the
     * `byteArrayOf(...)` builder requires `Byte`, not
     * `Int`. This helper is a tiny `toByte()` wrapper
     * to keep the byte table readable.
     */
    private fun b(int: Int): Byte = int.toByte()

    /**
     * The `BASIC_GAMEPAD_V1` descriptor.
     *
     * The byte sequence follows the USB HID 1.5 spec:
     * each item is a `bTag`, `bSize`, `data...` triple.
     * The full TLV tree:
     *
     * ```
     * USAGE_PAGE(Generic Desktop)              0x05 0x01
     * USAGE(Gamepad)                            0x09 0x05
     * COLLECTION(Application)                    0xA1 0x01
     *   USAGE_PAGE(Generic Desktop)             0x05 0x01
     *   USAGE(Pointer)                          0x09 0x01
     *   COLLECTION(Physical)                    0xA1 0x00
     *     USAGE(X), USAGE(Y)                    0x09 0x30 0x09 0x31
     *     LOGICAL_MIN(-127), LOGICAL_MAX(127)   0x15 0x81 0x25 0x7F
     *     REPORT_SIZE(8), REPORT_COUNT(2)      0x75 0x08 0x95 0x02
     *     INPUT(Data, Var, Abs)                0x81 0x02
     *   END_COLLECTION                          0xC0
     *   # same for right stick                 ...
     *   USAGE_PAGE(Generic Desktop)             0x05 0x01
     *   USAGE(Hat switch)                       0x09 0x39
     *   LOGICAL_MIN(0), LOGICAL_MAX(7)         0x15 0x00 0x25 0x07
     *   PHYSICAL_MIN(0), PHYSICAL_MAX(315)     0x35 0x00 0x46 0x3B 0x01
     *   UNIT(Degrees)                            0x65 0x14
     *   REPORT_SIZE(4), REPORT_COUNT(1)        0x75 0x04 0x95 0x01
     *   INPUT(Data, Var, Abs, Null)            0x81 0x42
     *   USAGE_PAGE(Buttons)                      0x05 0x09
     *   USAGE_MIN(Button 1), USAGE_MAX(Button 16)
     *                                          0x19 0x01 0x29 0x10
     *   LOGICAL_MIN(0), LOGICAL_MAX(1)         0x15 0x00 0x25 0x01
     *   REPORT_SIZE(1), REPORT_COUNT(16)       0x75 0x01 0x95 0x10
     *   INPUT(Data, Var, Abs)                  0x81 0x02
     * END_COLLECTION                            0xC0
     * ```
     *
     * The right-stick collection is identical to the
     * left-stick collection; both are emitted
     * contiguously. The full byte array is in the
     * [BASIC_GAMEPAD_V1_DESCRIPTOR] constant.
     */
    val BASIC_GAMEPAD_V1_DESCRIPTOR: ByteArray = byteArrayOf(
        // USAGE_PAGE(Generic Desktop) 0x05 0x01
        b(0x05), b(0x01),
        // USAGE(Gamepad) 0x09 0x05
        b(0x09), b(0x05),
        // COLLECTION(Application) 0xA1 0x01
        b(0xA1), b(0x01),
        // ----- Left stick: Pointer + Physical -----
        // USAGE_PAGE(Generic Desktop) 0x05 0x01
        b(0x05), b(0x01),
        // USAGE(Pointer) 0x09 0x01
        b(0x09), b(0x01),
        // COLLECTION(Physical) 0xA1 0x00
        b(0xA1), b(0x00),
        // USAGE(X) 0x09 0x30
        b(0x09), b(0x30),
        // USAGE(Y) 0x09 0x31
        b(0x09), b(0x31),
        // LOGICAL_MINIMUM(-127) 0x15 0x81
        b(0x15), b(0x81),
        // LOGICAL_MAXIMUM(127) 0x25 0x7F
        b(0x25), b(0x7F),
        // REPORT_SIZE(8) 0x75 0x08
        b(0x75), b(0x08),
        // REPORT_COUNT(2) 0x95 0x02
        b(0x95), b(0x02),
        // INPUT(Data, Var, Abs) 0x81 0x02
        b(0x81), b(0x02),
        // END_COLLECTION 0xC0
        b(0xC0),
        // ----- Right stick: Pointer + Physical -----
        // USAGE_PAGE(Generic Desktop) 0x05 0x01
        b(0x05), b(0x01),
        // USAGE(Pointer) 0x09 0x01
        b(0x09), b(0x01),
        // COLLECTION(Physical) 0xA1 0x00
        b(0xA1), b(0x00),
        // USAGE(X) 0x09 0x30
        b(0x09), b(0x30),
        // USAGE(Y) 0x09 0x31
        b(0x09), b(0x31),
        // LOGICAL_MINIMUM(-127) 0x15 0x81
        b(0x15), b(0x81),
        // LOGICAL_MAXIMUM(127) 0x25 0x7F
        b(0x25), b(0x7F),
        // REPORT_SIZE(8) 0x75 0x08
        b(0x75), b(0x08),
        // REPORT_COUNT(2) 0x95 0x02
        b(0x95), b(0x02),
        // INPUT(Data, Var, Abs) 0x81 0x02
        b(0x81), b(0x02),
        // END_COLLECTION 0xC0
        b(0xC0),
        // ----- Hat switch -----
        // USAGE_PAGE(Generic Desktop) 0x05 0x01
        b(0x05), b(0x01),
        // USAGE(Hat switch) 0x09 0x39
        b(0x09), b(0x39),
        // LOGICAL_MINIMUM(0) 0x15 0x00
        b(0x15), b(0x00),
        // LOGICAL_MAXIMUM(7) 0x25 0x07
        b(0x25), b(0x07),
        // PHYSICAL_MINIMUM(0) 0x35 0x00
        b(0x35), b(0x00),
        // PHYSICAL_MAXIMUM(315) 0x46 0x3B 0x01
        b(0x46), b(0x3B), b(0x01),
        // UNIT(Degrees) 0x65 0x14
        b(0x65), b(0x14),
        // REPORT_SIZE(4) 0x75 0x04
        b(0x75), b(0x04),
        // REPORT_COUNT(1) 0x95 0x01
        b(0x95), b(0x01),
        // INPUT(Data, Var, Abs, Null) 0x81 0x42
        b(0x81), b(0x42),
        // ----- 16 Buttons -----
        // USAGE_PAGE(Buttons) 0x05 0x09
        b(0x05), b(0x09),
        // USAGE_MINIMUM(Button 1) 0x19 0x01
        b(0x19), b(0x01),
        // USAGE_MAXIMUM(Button 16) 0x29 0x10
        b(0x29), b(0x10),
        // LOGICAL_MINIMUM(0) 0x15 0x00
        b(0x15), b(0x00),
        // LOGICAL_MAXIMUM(1) 0x25 0x01
        b(0x25), b(0x01),
        // REPORT_SIZE(1) 0x75 0x01
        b(0x75), b(0x01),
        // REPORT_COUNT(16) 0x95 0x10
        b(0x95), b(0x10),
        // INPUT(Data, Var, Abs) 0x81 0x02
        b(0x81), b(0x02),
        // END_COLLECTION(Application) 0xC0
        b(0xC0)
    )

    /**
     * The report ID byte for [BASIC_GAMEPAD_V1]. Every
     * report the host sends starts with this byte.
     */
    const val BASIC_GAMEPAD_V1_REPORT_ID: Byte = 0x01

    /**
     * The total report size in bytes (excluding the
     * report ID). The host's report writer prepends
     * the report ID, so the on-the-wire frame is
     * `BASIC_GAMEPAD_V1_REPORT_ID + reportBytes`.
     *
     * Breakdown:
     *  - 1 byte: hat switch (4 bits used, 4 bits padding)
     *  - 2 bytes: 16 buttons (16 bits = 2 bytes)
     *  - 4 bytes: left stick (X + Y, 2 bytes each)
     *  - 4 bytes: right stick (X + Y, 2 bytes each)
     *  - 1 byte: left trigger
     *  - 1 byte: right trigger
     *  - total: 13 bytes
     *
     * (13 = 1 + 2 + 4 + 4 + 1 + 1)
     */
    const val BASIC_GAMEPAD_V1_REPORT_SIZE: Int = 13

    /**
     * The friendly name of the descriptor family. Used in
     * the `BluetoothHidDevice` `setDeviceName(...)` call
     * and in the compatibility database.
     */
    const val DEVICE_NAME: String = "Elysium Nexus Gamepad"
}
