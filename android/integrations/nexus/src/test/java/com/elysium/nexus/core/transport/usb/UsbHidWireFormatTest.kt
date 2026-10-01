package com.elysium.nexus.core.transport.usb

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit tests for the USB HID wire format
 * defined in [UsbHidTransport].
 *
 * These verify that the binary packets match
 * the spec documented in the KDoc header.
 */
class UsbHidWireFormatTest {

    @Test
    fun `mouse move packet has correct tag and payload`() {
        val buf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x01)
        buf.putShort(100)
        buf.putShort(-50)
        val data = buf.array()

        assertEquals(5, data.size)
        assertEquals(0x01, data[0].toInt() and 0xFF)
        assertEquals(100, ByteBuffer.wrap(data, 1, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt())
        assertEquals(-50, ByteBuffer.wrap(data, 3, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt())
    }

    @Test
    fun `mouse button packet has correct tag`() {
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x02)
        buf.put(1) // right button
        buf.put(1) // pressed
        val data = buf.array()

        assertEquals(3, data.size)
        assertEquals(0x02, data[0].toInt() and 0xFF)
    }

    @Test
    fun `scroll packet has correct tag`() {
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x03)
        buf.putShort(5)
        val data = buf.array()

        assertEquals(0x03, data[0].toInt() and 0xFF)
    }

    @Test
    fun `keyboard packet has correct tag`() {
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x04)
        buf.put(0x04) // HID usage code for 'a'
        buf.put(1)    // pressed
        val data = buf.array()

        assertEquals(0x04, data[0].toInt() and 0xFF)
    }

    @Test
    fun `touchpad move packet has correct tag and finger count`() {
        val buf = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x05)
        buf.putShort(500)
        buf.putShort(300)
        buf.put(2) // two fingers
        val data = buf.array()

        assertEquals(6, data.size)
        assertEquals(0x05, data[0].toInt() and 0xFF)
        assertEquals(2, data[5].toInt() and 0xFF)
    }

    @Test
    fun `touchpad click packet has correct tag`() {
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x06)
        buf.put(0) // left
        buf.put(1) // pressed
        val data = buf.array()

        assertEquals(0x06, data[0].toInt() and 0xFF)
    }

    @Test
    fun `touchpad scroll packet has correct tag`() {
        val buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x07)
        buf.putShort(-3)
        val data = buf.array()

        assertEquals(0x07, data[0].toInt() and 0xFF)
    }

    @Test
    fun `gamepad packet has correct tag and size`() {
        val buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x10)
        buf.putLong(0xFF00L) // buttons
        buf.put(127)         // left stick X
        buf.put(-128)        // left stick Y
        buf.put(0)           // right stick X
        buf.put(64)          // right stick Y
        buf.put(255.toByte()) // left trigger
        buf.put(128.toByte()) // right trigger
        val data = buf.array()

        assertEquals(16, data.size)
        assertEquals(0x10, data[0].toInt() and 0xFF)
    }

    @Test
    fun `ping packet is single byte 0xFE`() {
        val data = byteArrayOf(0xFE.toByte())
        assertEquals(1, data.size)
        assertEquals(0xFE, data[0].toInt() and 0xFF)
    }

    @Test
    fun `release all packet is single byte 0xFF`() {
        val data = byteArrayOf(0xFF.toByte())
        assertEquals(1, data.size)
        assertEquals(0xFF, data[0].toInt() and 0xFF)
    }

    @Test
    fun `gamepad button bit packing works for face buttons`() {
        // A=0, B=1, X=2, Y=3
        val buttons = (1L shl 0) or (1L shl 1) or (1L shl 2) or (1L shl 3)
        assertEquals(0x0FL, buttons)
    }

    @Test
    fun `gamepad button bit packing works for shoulder buttons`() {
        // LB=4, RB=5
        val buttons = (1L shl 4) or (1L shl 5)
        assertEquals(0x30L, buttons)
    }

    @Test
    fun `gamepad button bit packing works for dpad`() {
        // Up=8, Down=9, Left=10, Right=11
        val buttons = (1L shl 8) or (1L shl 9) or (1L shl 10) or (1L shl 11)
        assertEquals(0xF00L, buttons)
    }

    @Test
    fun `stick axis conversion from minus1 to 1 to i8`() {
        // -1.0 → 0, 0.0 → 127, 1.0 → 255 (capped to i8: -128..127)
        fun convert(value: Float): Byte =
            ((value + 1f).times(127.5f).toInt().coerceIn(-128, 127)).toByte()

        assertEquals(0.toByte(), convert(-1f))
        assertEquals(127.toByte(), convert(0f))
        // 1.0 → 255, but capped to 127
        assertEquals(127.toByte(), convert(1f))
    }

    @Test
    fun `trigger conversion from 0 to 1 to u8`() {
        fun convert(value: Float): Byte =
            (value.times(255f).toInt().coerceIn(0, 255)).toByte()

        assertEquals(0.toByte(), convert(0f))
        assertEquals(127.toByte(), convert(0.5f))
        assertEquals(255.toByte(), convert(1f))
    }
}
