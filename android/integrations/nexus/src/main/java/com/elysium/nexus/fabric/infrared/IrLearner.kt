package com.elysium.nexus.fabric.infrared

import kotlin.math.abs
import kotlin.math.roundToInt

object IrLearner {

    data class LearnResult(
        val carrierHz: Int,
        val command: IrCommand?,
        val rawWaveform: IrWaveform,
        val confidence: Float
    )

    fun learn(raw: IntArray, sampleRateHz: Int = 1_000_000, measuredCarrierHz: Int? = null): LearnResult {
        require(raw.size >= 2) {
            "IrLearner.learn: raw waveform must have at least 2 entries."
        }
        require(raw.all { it > 0 }) {
            "IrLearner.learn: raw waveform slice durations must be strictly positive (> 0 us)."
        }
        require(sampleRateHz > 0) {
            "IrLearner.learn: sampleRateHz must be positive."
        }

        // V0.7 Phase 10: Measured raw carrier is AUTHORITATIVE physical evidence when present.
        val carrier = if (measuredCarrierHz != null && measuredCarrierHz in 30_000..60_000) {
            measuredCarrierHz
        } else {
            estimateCarrier(raw, sampleRateHz)
        }
        val safeCarrier = if (carrier in 30_000..60_000) carrier else IrProtocol.DEFAULT_CARRIER_HZ

        val candidates: List<MatchCandidate> = listOf(
            MatchCandidate(IrProtocol.Nec) {
                tryDecodeNec(raw, IrProtocol.Nec.carrierHz)
            },
            MatchCandidate(IrProtocol.NecExtended) {
                tryDecodeNecExtended(raw, IrProtocol.NecExtended.carrierHz)
            },
            MatchCandidate(IrProtocol.Rc5) {
                tryDecodeRc5(raw, IrProtocol.Rc5.carrierHz)
            },
            MatchCandidate(IrProtocol.SonySirc) {
                tryDecodeSonySirc(raw, IrProtocol.SonySirc.carrierHz)
            },
            MatchCandidate(IrProtocol.Samsung) {
                tryDecodeSamsung(raw, IrProtocol.Samsung.carrierHz)
            }
        )

        for (candidate in candidates) {
            val cmd = candidate.decoder()
            if (cmd != null) {
                val confidence = 0.85f + (1.0f - 0.85f) * confidenceFactor(raw, cmd)
                return LearnResult(
                    carrierHz = safeCarrier,
                    command = cmd,
                    rawWaveform = IrWaveform(
                        carrierHz = candidate.protocol.carrierHz,
                        pattern = raw
                    ),
                    confidence = confidence.coerceIn(0f, 1f)
                )
            }
        }

        return LearnResult(
            carrierHz = safeCarrier,
            command = null,
            rawWaveform = IrWaveform(
                carrierHz = safeCarrier,
                pattern = raw
            ),
            confidence = 0.3f
        )
    }

    private fun tryDecodeNec(raw: IntArray, carrierHz: Int): IrCommand? {
        val waveform = try { IrWaveform(carrierHz = carrierHz, pattern = raw) } catch (e: Throwable) { return null }
        return IrWaveform.decodeNec(waveform)?.let { nec ->
            IrCommand(
                protocol = IrProtocol.Nec,
                address = nec.address,
                command = nec.command
            )
        }
    }

    internal fun estimateCarrier(raw: IntArray, sampleRateHz: Int): Int {
        val onPulses = raw.indices.filter { it % 2 == 0 }.map { raw[it] }.filter { it > 0 }
        if (onPulses.isEmpty()) return IrProtocol.DEFAULT_CARRIER_HZ
        val shortest = onPulses.min()
        if (shortest >= 1_000) {
            return IrProtocol.DEFAULT_CARRIER_HZ
        }
        val estimated = (shortest.toDouble() / 560.0 * 38_000.0).roundToInt()
        return estimated.coerceIn(30_000, 60_000)
    }

    private fun confidenceFactor(raw: IntArray, cmd: IrCommand): Float {
        val expected = when (cmd.protocol) {
            IrProtocol.Nec -> 67
            IrProtocol.NecExtended -> 67
            IrProtocol.Rc5 -> 28
            IrProtocol.SonySirc -> 26
            IrProtocol.Samsung -> 67
            else -> raw.size
        }
        val diff = abs(raw.size - expected)
        return (1.0f - diff.toFloat() / expected.toFloat()).coerceIn(0f, 1f)
    }

    private fun tryDecodeNecExtended(raw: IntArray, carrierHz: Int): IrCommand? {
        val waveform = try { IrWaveform(carrierHz = carrierHz, pattern = raw) } catch (e: Throwable) { return null }
        return IrWaveform.decodeNecExtended(waveform)?.let { necx ->
            IrCommand(
                protocol = IrProtocol.NecExtended,
                address = necx.address,
                command = necx.command
            )
        }
    }

    private fun tryDecodeRc5(raw: IntArray, carrierHz: Int): IrCommand? {
        val waveform = try { IrWaveform(carrierHz = carrierHz, pattern = raw) } catch (e: Throwable) { return null }
        return IrWaveform.decodeRc5(waveform)?.let { rc5 ->
            IrCommand(
                protocol = IrProtocol.Rc5,
                address = rc5.address,
                command = rc5.command,
                extras = mapOf("toggle" to rc5.toggle.toString())
            )
        }
    }

    private fun tryDecodeSonySirc(raw: IntArray, carrierHz: Int): IrCommand? {
        val waveform = try { IrWaveform(carrierHz = carrierHz, pattern = raw) } catch (e: Throwable) { return null }
        return IrWaveform.decodeSonySirc(waveform)?.let { sirc ->
            IrCommand(
                protocol = IrProtocol.SonySirc,
                address = sirc.address,
                command = sirc.command,
                extras = if (sirc.extended) mapOf("extended" to "true") else emptyMap()
            )
        }
    }

    private fun tryDecodeSamsung(raw: IntArray, carrierHz: Int): IrCommand? {
        val waveform = try { IrWaveform(carrierHz = carrierHz, pattern = raw) } catch (e: Throwable) { return null }
        return IrWaveform.decodeSamsung(waveform)?.let { sam ->
            IrCommand(
                protocol = IrProtocol.Samsung,
                address = sam.address,
                command = sam.command
            )
        }
    }

    private data class MatchCandidate(
        val protocol: IrProtocol,
        val decoder: () -> IrCommand?
    )
}

data class IrCommand(
    val protocol: IrProtocol,
    val address: Int,
    val command: Int,
    val extras: Map<String, String> = emptyMap()
) {
    init {
        require(address >= 0) {
            "IrCommand.address must be non-negative (got $address)."
        }
        require(command >= 0) {
            "IrCommand.command must be non-negative (got $command)."
        }
    }
}
