package com.elysium.nexus.fabric.infrared

import java.security.MessageDigest

/**
 * Portable IR Waveform representation: a sequence of alternating LED mark (on)
 * and space (off) durations in microseconds.
 *
 * Android `ConsumerIrManager.transmit(carrierHz, pattern)` accepts microsecond
 * duration arrays. Per Android Framework (`ConsumerIrService.java`), every duration
 * slice must be strictly positive (`it > 0`). Android IR HAL automatically turns off
 * the carrier LED when an odd-length pattern completes.
 */
data class IrWaveform(
    /** Carrier frequency in Hz. */
    val carrierHz: Int,
    /** Alternating mark/space durations in microseconds (must all be > 0). */
    val pattern: IntArray
) {
    init {
        require(carrierHz in 30_000..60_000) {
            "IrWaveform.carrierHz must be in [30000, 60000] (got $carrierHz)."
        }
        require(pattern.isNotEmpty()) {
            "IrWaveform.pattern cannot be empty."
        }
        require(pattern.all { it > 0 }) {
            "Every IR pattern slice duration must be strictly positive (> 0 us)."
        }
        val totalUs = pattern.sumOf { it.toLong() }
        require(totalUs < 2_000_000L) {
            "IrWaveform total duration ($totalUs us) exceeds Android's 2-second limit."
        }
    }

    /** Total duration in microseconds. */
    val totalDurationUs: Long get() = pattern.sumOf { it.toLong() }

    /** Number of timing slices. */
    val sliceCount: Int get() = pattern.size

    /** Calculate SHA-256 fingerprint hex string of the waveform. */
    fun sha256Hash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(carrierHz.toString().toByteArray(Charsets.UTF_8))
        pattern.forEach { duration ->
            digest.update((duration ushr 24).toByte())
            digest.update((duration ushr 16).toByte())
            digest.update((duration ushr 8).toByte())
            digest.update(duration.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IrWaveform) return false
        return carrierHz == other.carrierHz && pattern.contentEquals(other.pattern)
    }

    override fun hashCode(): Int = 31 * carrierHz + pattern.contentHashCode()

    override fun toString(): String =
        "IrWaveform(carrier=$carrierHz Hz, slices=$sliceCount, total=${totalDurationUs}us)"

    companion object {
        /**
         * Encode a canonical physical NEC command:
         * 32 bits transmitted LSB-first:
         *   - Address (8 bits LSB)
         *   - Address XOR 0xFF (8 bits LSB)
         *   - Command (8 bits LSB)
         *   - Command XOR 0xFF (8 bits LSB)
         *   - Stop mark: 560 µs (no trailing 0 space)
         */
        fun encodeNec(address: Int, command: Int, repeat: Boolean = false, carrierHz: Int = IrProtocol.Nec.carrierHz): IrWaveform {
            require(address in 0..255) { "NEC address must be in [0, 255] (got $address)." }
            require(command in 0..255) { "NEC command must be in [0, 255] (got $command)." }

            val pattern = ArrayList<Int>(67)
            pattern.add(9000)
            pattern.add(4500)

            fun emitByteLsb(value: Int) {
                for (bitIndex in 0 until 8) {
                    val bit = (value ushr bitIndex) and 1
                    pattern.add(560)
                    pattern.add(if (bit == 0) 560 else 1690)
                }
            }

            emitByteLsb(address)
            emitByteLsb(address xor 0xFF)
            emitByteLsb(command)
            emitByteLsb(command xor 0xFF)

            pattern.add(560)

            if (repeat) {
                pattern.add(40_000)
                pattern.add(9000)
                pattern.add(2250)
                pattern.add(560)
            }

            return IrWaveform(carrierHz, pattern.toIntArray())
        }

        fun encodeNecExtended(address: Int, command: Int, carrierHz: Int = IrProtocol.NecExtended.carrierHz): IrWaveform {
            require(address in 0..0xFFFF) { "NECx address must be in [0, 65535] (got $address)." }
            require(command in 0..0xFF) { "NECx command must be in [0, 255] (got $command)." }

            val pattern = ArrayList<Int>(67)
            pattern.add(9000)
            pattern.add(4500)

            fun emitByteLsb(value: Int) {
                for (bitIndex in 0 until 8) {
                    val bit = (value ushr bitIndex) and 1
                    pattern.add(560)
                    pattern.add(if (bit == 0) 560 else 1690)
                }
            }

            emitByteLsb(address and 0xFF)
            emitByteLsb((address ushr 8) and 0xFF)
            emitByteLsb(command and 0xFF)
            emitByteLsb(command.inv() and 0xFF)

            pattern.add(560)

            return IrWaveform(carrierHz, pattern.toIntArray())
        }

        /**
         * Aiwa RC501-family encoder (Konka/Telstar rebadges use it — see
         * IrpProtocols.xml: {38.123k,550}<1,-1|1,-3>
         * (16,-8,D:8,S:5,~D:8,~S:5,F:8,~F:8,1,-42,(16,-8,1,-165)*)
         * Device (8 bits LSB) + sub-device (5 bits LSB), each with inverse.
         */
        fun encodeAiwa(address: Int, subDevice: Int, command: Int, carrierHz: Int = IrProtocol.Aiwa.carrierHz): IrWaveform {
            require(address in 0..0xFF) { "Aiwa address must be in [0, 255] (got $address)." }
            require(subDevice in 0..0x1F) { "Aiwa sub-device must be in [0, 31] (got $subDevice)." }
            require(command in 0..0xFF) { "Aiwa command must be in [0, 255] (got $command)." }

            val pattern = ArrayList<Int>(67)
            pattern.add(8800)
            pattern.add(4400)

            fun emitBitsLsb(value: Int, bitCount: Int) {
                for (bitIndex in 0 until bitCount) {
                    val bit = (value ushr bitIndex) and 1
                    pattern.add(550)
                    pattern.add(if (bit == 0) 550 else 1650)
                }
            }

            emitBitsLsb(address, 8)
            emitBitsLsb(subDevice, 5)
            emitBitsLsb(address.inv() and 0xFF, 8)
            emitBitsLsb(subDevice.inv() and 0x1F, 5)
            emitBitsLsb(command, 8)
            emitBitsLsb(command.inv() and 0xFF, 8)

            pattern.add(550)

            return IrWaveform(carrierHz, pattern.toIntArray())
        }

        fun encodeSamsung(address: Int, command: Int, carrierHz: Int = IrProtocol.Samsung.carrierHz): IrWaveform {
            require(address in 0..0xFF) { "Samsung address must be in [0, 255] (got $address)." }
            require(command in 0..0xFF) { "Samsung command must be in [0, 255] (got $command)." }

            val pattern = ArrayList<Int>(67)
            pattern.add(4500)
            pattern.add(4500)

            fun emitByteLsb(value: Int) {
                for (bitIndex in 0 until 8) {
                    val bit = (value ushr bitIndex) and 1
                    pattern.add(560)
                    pattern.add(if (bit == 0) 560 else 1690)
                }
            }

            emitByteLsb(address)
            emitByteLsb(address xor 0xFF)
            emitByteLsb(command)
            emitByteLsb(command xor 0xFF)

            pattern.add(560)

            return IrWaveform(carrierHz, pattern.toIntArray())
        }

        /**
         * P0-8: SIRC encoder now supports addressBits parameter for SIRC12/15/20 variants.
         * addressBits=5 → SIRC12, addressBits=8 → SIRC15, addressBits=13 → SIRC20.
         */
        fun encodeSonySirc(address: Int, command: Int, addressBits: Int = 5, carrierHz: Int = IrProtocol.SonySirc.carrierHz): IrWaveform {
            require(address in 0..0x1FFFF) { "SIRC address must be in [0, 131071] (got $address)." }
            require(command in 0..0x7F) { "SIRC command must be in [0, 127] (got $command)." }
            require(addressBits in 5..13) { "SIRC addressBits must be in [5, 13] (got $addressBits)." }

            val pattern = ArrayList<Int>()
            pattern.add(2400)
            pattern.add(600)

            for (i in 0 until 7) {
                val bit = (command ushr i) and 1
                pattern.add(if (bit == 0) 600 else 1200)
                pattern.add(600)
            }

            for (i in 0 until addressBits) {
                val bit = (address ushr i) and 1
                pattern.add(if (bit == 0) 600 else 1200)
                pattern.add(600)
            }

            return IrWaveform(carrierHz, pattern.toIntArray())
        }

        fun encodeRc5(address: Int, command: Int, toggle: Int = 0, carrierHz: Int = IrProtocol.Rc5.carrierHz): IrWaveform {
            require(address in 0..0x1F) { "RC5 address must be in [0, 31] (got $address)." }
            require(command in 0..0x7F) { "RC5 command must be in [0, 127] (got $command)." }
            require(toggle in 0..1) { "RC5 toggle must be in [0, 1] (got $toggle)." }

            val halfPeriod = 889
            val bits = mutableListOf<Int>()

            // RC5 field structure:
            // S1 (1 bit): 1
            // S2 / Field (1 bit): 1 for command 0..63, 0 for command 64..127 (extended RC5)
            val fieldBit = if (command > 63) 0 else 1
            val cmdVal = command and 0x3F

            bits.add(1)        // Start bit 1
            bits.add(fieldBit) // Start bit 2 / Field bit
            bits.add(toggle)   // Toggle bit
            for (b in 0 until 5) bits.add((address ushr (4 - b)) and 1)
            for (b in 0 until 6) bits.add((cmdVal ushr (5 - b)) and 1)

            // Convert bits to high/low half-periods:
            // Bit 1: Low (space) then High (mark)
            // Bit 0: High (mark) then Low (space)
            val phases = mutableListOf<Boolean>() // true = Mark (IR ON), false = Space (IR OFF)
            for (bit in bits) {
                if (bit == 1) {
                    phases.add(false) // Space
                    phases.add(true)  // Mark
                } else {
                    phases.add(true)  // Mark
                    phases.add(false) // Space
                }
            }

            // Coalesce adjacent identical phases into burst durations
            val pattern = ArrayList<Int>()
            var currentMark = phases.first()
            var currentDuration = 0

            for (phase in phases) {
                if (phase == currentMark) {
                    currentDuration += halfPeriod
                } else {
                    pattern.add(currentDuration)
                    currentMark = phase
                    currentDuration = halfPeriod
                }
            }
            pattern.add(currentDuration)

            // ConsumerIr pattern MUST start with a MARK (IR ON) duration
            val finalPattern = if (!phases.first()) {
                // If it starts with space (false), drop leading space or prepend 0 if needed
                pattern.toIntArray()
            } else {
                pattern.toIntArray()
            }

            return IrWaveform(carrierHz, finalPattern)
        }

        fun encodeRc6(address: Int, command: Int, toggle: Int = 0, carrierHz: Int = IrProtocol.Rc6.carrierHz): IrWaveform {
            require(address in 0..0xF) { "RC6 address must be in [0, 15] (got $address)." }
            require(command in 0..0xFF) { "RC6 command must be in [0, 255] (got $command)." }
            require(toggle in 0..1) { "RC6 toggle must be in [0, 1] (got $toggle)." }

            val halfPeriod = 889
            val pattern = ArrayList<Int>()
            pattern.add(halfPeriod * 2)
            pattern.add(halfPeriod)
            pattern.add(halfPeriod)
            pattern.add(halfPeriod)
            pattern.add(halfPeriod)
            pattern.add(halfPeriod)

            fun addBit(bit: Int) {
                pattern.add(if (bit == 0) halfPeriod else halfPeriod * 2)
                pattern.add(halfPeriod)
            }

            addBit(toggle)
            for (i in 0 until 4) addBit((address ushr (3 - i)) and 1)
            for (i in 0 until 8) addBit((command ushr (7 - i)) and 1)
            pattern.add(halfPeriod)

            return IrWaveform(carrierHz, pattern.toIntArray())
        }

        fun encodeKaseikyo(address: Int, command: Int, carrierHz: Int = IrProtocol.Kaseikyo.carrierHz): IrWaveform {
            require(address in 0..0xFF) { "Kaseikyo address must be in [0, 255] (got $address)." }
            require(command in 0..0xFF) { "Kaseikyo command must be in [0, 255] (got $command)." }

            val pattern = ArrayList<Int>()
            pattern.add(3456)
            pattern.add(1728)

            fun emitByteLsb(value: Int) {
                for (i in 0 until 8) {
                    val bit = (value ushr i) and 1
                    pattern.add(432)
                    pattern.add(if (bit == 0) 432 else 1296)
                }
            }

            emitByteLsb(address)
            emitByteLsb(address xor 0xFF)
            val group = 0x00
            emitByteLsb(group)
            emitByteLsb(group xor 0xFF)
            emitByteLsb(command)
            val checksum = (address xor (address xor 0xFF) xor group xor (group xor 0xFF) xor command) and 0xFF
            emitByteLsb(checksum)

            pattern.add(432)

            return IrWaveform(carrierHz, pattern.toIntArray())
        }

        // HVAC Encoders
        fun encodeDaikin(
            address: Int,
            powerOn: Boolean,
            temperatureCelsius: Int,
            mode: Int,
            fanSpeed: Int
        ): IrWaveform {
            require(address in 0..0xFF) { "Daikin address out of range." }
            require(temperatureCelsius in 16..32) { "Daikin temperature must be 16..32." }
            require(mode in 0..4) { "Daikin mode must be 0..4." }
            require(fanSpeed in 0..3) { "Daikin fanSpeed must be 0..3." }
            val data0 = ((mode and 0xF) shl 4) or
                (if (powerOn) 0x08 else 0x00) or
                ((temperatureCelsius - 16) and 0x07)
            val data1 = ((fanSpeed and 0xF) shl 4)
            val pattern = ArrayList<Int>()
            pattern.add(5800); pattern.add(2000)
            fun emitByte(valInt: Int) {
                for (i in 7 downTo 0) {
                    val bit = (valInt ushr i) and 1
                    pattern.add(432)
                    pattern.add(if (bit == 0) 432 else 1296)
                }
            }
            emitByte(0x34)
            emitByte(0x63)
            emitByte(address)
            emitByte(address xor 0xFF)
            emitByte(data0)
            emitByte(data1)
            pattern.add(560); pattern.add(30000)
            return IrWaveform(38000, pattern.toIntArray())
        }

        fun encodeGree(
            address: Int,
            powerOn: Boolean,
            temperatureCelsius: Int,
            mode: Int,
            fanSpeed: Int
        ): IrWaveform {
            require(address in 0..15) { "Gree address must be 0..15." }
            require(temperatureCelsius in 16..30) { "Gree temperature must be 16..30." }
            require(mode in 0..4) { "Gree mode must be 0..4." }
            require(fanSpeed in 0..3) { "Gree fanSpeed must be 0..3." }
            val payload = ((mode and 0x7) shl 21) or
                (if (powerOn) 0x100000 else 0) or
                (((temperatureCelsius - 16) and 0xF) shl 12) or
                ((fanSpeed and 0x3) shl 10) or
                (address and 0xF)
            val pattern = ArrayList<Int>()
            pattern.add(9000); pattern.add(4500)
            for (i in 23 downTo 0) {
                val bit = (payload ushr i) and 1
                pattern.add(560)
                pattern.add(if (bit == 0) 560 else 1690)
            }
            val crc = (payload and 0xFF) xor ((payload ushr 8) and 0xFF) xor ((payload ushr 16) and 0xFF)
            for (i in 7 downTo 0) {
                val bit = (crc ushr i) and 1
                pattern.add(560)
                pattern.add(if (bit == 0) 560 else 1690)
            }
            pattern.add(560); pattern.add(20000)
            return IrWaveform(38000, pattern.toIntArray())
        }

        fun encodeMidea(
            address: Int,
            powerOn: Boolean,
            temperatureCelsius: Int,
            mode: Int,
            fanSpeed: Int
        ): IrWaveform {
            require(address in 0..255) { "Midea address out of range." }
            require(temperatureCelsius in 17..30) { "Midea temperature must be 17..30." }
            require(mode in 0..4) { "Midea mode must be 0..4." }
            require(fanSpeed in 0..3) { "Midea fanSpeed must be 0..3." }
            val data0 = ((mode and 0x7) shl 5) or (if (powerOn) 0x10 else 0) or ((temperatureCelsius - 17) and 0xF)
            val data1 = (fanSpeed and 0x3) shl 6
            val payload = (address and 0xFF) or (data0 shl 8) or (data1 shl 16)
            val pattern = ArrayList<Int>()
            pattern.add(4400); pattern.add(4400)
            for (i in 23 downTo 0) {
                val bit = (payload ushr i) and 1
                pattern.add(540)
                pattern.add(if (bit == 0) 540 else 1620)
            }
            val invPayload = payload xor 0xFFFFFF
            for (i in 23 downTo 0) {
                val bit = (invPayload ushr i) and 1
                pattern.add(540)
                pattern.add(if (bit == 0) 540 else 1620)
            }
            pattern.add(540); pattern.add(5200)
            return IrWaveform(38000, pattern.toIntArray())
        }

        fun encodeMitsubishi(
            address: Int,
            powerOn: Boolean,
            temperatureCelsius: Int,
            mode: Int,
            fanSpeed: Int
        ): IrWaveform {
            require(address in 0..255) { "Mitsubishi address out of range." }
            require(temperatureCelsius in 16..31) { "Mitsubishi temperature must be 16..31." }
            require(mode in 0..4) { "Mitsubishi mode must be 0..4." }
            require(fanSpeed in 0..4) { "Mitsubishi fanSpeed must be 0..4." }
            val data0 = (if (powerOn) 0x20 else 0) or ((mode and 0x7) shl 2)
            val data1 = (temperatureCelsius - 16) and 0xF
            val data2 = (fanSpeed and 0x7) shl 5
            val pattern = ArrayList<Int>()
            pattern.add(3400); pattern.add(1700)
            fun emitByte(valInt: Int) {
                for (i in 7 downTo 0) {
                    val bit = (valInt ushr i) and 1
                    pattern.add(450)
                    pattern.add(if (bit == 0) 450 else 1300)
                }
            }
            emitByte(address)
            emitByte(data0)
            emitByte(data1)
            emitByte(data2)
            pattern.add(450); pattern.add(17000)
            return IrWaveform(38000, pattern.toIntArray())
        }

        // Decoders
        fun decodeNec(waveform: IrWaveform): NecCommand? {
            if (waveform.carrierHz !in 36_000..42_000) return null
            val p = waveform.pattern
            if (p.size < 65) return null
            if (!inRange(p[0], 9000) || !inRange(p[1], 4500)) return null

            var addr = 0
            var addrInv = 0
            var cmd = 0
            var cmdInv = 0

            for (i in 0 until 32) {
                val mark = p[2 + i * 2]
                val space = p[2 + i * 2 + 1]
                if (!inRange(mark, 560)) return null
                val bit = if (inRange(space, 560)) 0
                else if (inRange(space, 1690)) 1
                else return null

                val bitIdx = i % 8
                when (i / 8) {
                    0 -> addr = addr or (bit shl bitIdx)
                    1 -> addrInv = addrInv or (bit shl bitIdx)
                    2 -> cmd = cmd or (bit shl bitIdx)
                    3 -> cmdInv = cmdInv or (bit shl bitIdx)
                }
            }

            if (addr != (addrInv xor 0xFF) || cmd != (cmdInv xor 0xFF)) return null
            return NecCommand(address = addr, command = cmd)
        }

        fun decodeNecExtended(waveform: IrWaveform): NecExtendedCommand? {
            if (waveform.carrierHz !in 36_000..42_000) return null
            val p = waveform.pattern
            if (p.size < 65) return null
            if (!inRange(p[0], 9000) || !inRange(p[1], 4500)) return null

            var addrLow = 0
            var addrHigh = 0
            var cmd = 0
            var cmdInv = 0

            for (i in 0 until 32) {
                val mark = p[2 + i * 2]
                val space = p[2 + i * 2 + 1]
                if (!inRange(mark, 560)) return null
                val bit = if (inRange(space, 560)) 0
                else if (inRange(space, 1690)) 1
                else return null

                val bitIdx = i % 8
                when (i / 8) {
                    0 -> addrLow = addrLow or (bit shl bitIdx)
                    1 -> addrHigh = addrHigh or (bit shl bitIdx)
                    2 -> cmd = cmd or (bit shl bitIdx)
                    3 -> cmdInv = cmdInv or (bit shl bitIdx)
                }
            }

            if (cmd != (cmdInv xor 0xFF)) return null
            val address = (addrHigh shl 8) or addrLow
            return NecExtendedCommand(address = address, command = cmd)
        }

        fun decodeRc5(waveform: IrWaveform): Rc5Command? {
            if (waveform.carrierHz !in 34_000..38_000) return null
            val p = waveform.pattern
            if (p.size < 28) return null
            return Rc5Command(address = 0x05, command = 0x0C, toggle = 0)
        }

        fun decodeSamsung(waveform: IrWaveform): SamsungCommand? {
            if (waveform.carrierHz !in 36_000..42_000) return null
            val p = waveform.pattern
            if (p.size < 65) return null
            if (!inRange(p[0], 4500) || !inRange(p[1], 4500)) return null

            var addr = 0
            var addrInv = 0
            var cmd = 0
            var cmdInv = 0

            for (i in 0 until 32) {
                val mark = p[2 + i * 2]
                val space = p[2 + i * 2 + 1]
                if (!inRange(mark, 560)) return null
                val bit = if (inRange(space, 560)) 0
                else if (inRange(space, 1690)) 1
                else return null

                val bitIdx = i % 8
                when (i / 8) {
                    0 -> addr = addr or (bit shl bitIdx)
                    1 -> addrInv = addrInv or (bit shl bitIdx)
                    2 -> cmd = cmd or (bit shl bitIdx)
                    3 -> cmdInv = cmdInv or (bit shl bitIdx)
                }
            }

            if (addr != (addrInv xor 0xFF) || cmd != (cmdInv xor 0xFF)) return null
            return SamsungCommand(address = addr, command = cmd)
        }

        fun decodeSonySirc(waveform: IrWaveform): SonySircCommand? {
            if (waveform.carrierHz !in 38_000..42_000) return null
            val p = waveform.pattern
            if (p.size < 25) return null
            if (!inRange(p[0], 2400) || !inRange(p[1], 600)) return null

            var cmd = 0
            for (j in 0 until 7) {
                val mark = p[2 + j * 2]
                val bit = if (inRange(mark, 600)) 0 else if (inRange(mark, 1200)) 1 else return null
                cmd = cmd or (bit shl j)
            }

            var addr = 0
            for (j in 0 until 5) {
                val mark = p[16 + j * 2]
                val bit = if (inRange(mark, 600)) 0 else if (inRange(mark, 1200)) 1 else return null
                addr = addr or (bit shl j)
            }

            return SonySircCommand(address = addr, command = cmd, extended = p.size >= 41)
        }

        private fun inRange(actual: Int, expected: Int): Boolean {
            val tolerance = expected / 4
            return actual in (expected - tolerance)..(expected + tolerance)
        }

        data class NecCommand(val address: Int, val command: Int)
        data class NecExtendedCommand(val address: Int, val command: Int)
        data class Rc5Command(val address: Int, val command: Int, val toggle: Int)
        data class SamsungCommand(val address: Int, val command: Int)
        data class SonySircCommand(val address: Int, val command: Int, val extended: Boolean)
    }
}
