package com.elysium.nexus.fabric.infrared.ingestion

import com.elysium.nexus.fabric.infrared.IrWaveform
import java.security.MessageDigest

/**
 * The §5 IR data ingestion parser interface.
 *
 * Provides safe, memory-bounded parsing for external IR signal files
 * (Flipper `.ir`, LIRC `.lircd.conf`, Pronto Hex, SmartIR JSON, etc.).
 */
interface IrSourceParser {
    val formatName: String

    /**
     * Parse raw string/file content into a list of [ParsedIrSignal]s.
     */
    fun parse(content: String, sourceArtifactId: String): ParseResult
}

/**
 * A single parsed IR signal from an external source.
 */
data class ParsedIrSignal(
    val name: String,
    val carrierHz: Int,
    val waveform: IrWaveform,
    val protocolName: String? = null,
    val address: Int? = null,
    val command: Int? = null
)

/**
 * Result of parsing an external IR artifact.
 */
sealed class ParseResult {
    data class Success(val signals: List<ParsedIrSignal>) : ParseResult()
    data class Partial(val signals: List<ParsedIrSignal>, val warnings: List<String>) : ParseResult()
    data class Error(val reason: String) : ParseResult()
}

/**
 * License compliance gate for external IR sources (§6).
 */
object LicenseGate {
    enum class LicenseStatus { APPROVED, CONDITIONAL, BLOCKED }

    data class SourcePolicy(
        val sourceName: String,
        val licenseSpdx: String,
        val status: LicenseStatus,
        val attributionRequired: Boolean
    )

    private val POLICIES = mapOf(
        "flipper-irdb" to SourcePolicy("Flipper-IRDB", "CC0-1.0", LicenseStatus.APPROVED, attributionRequired = true),
        "smartir" to SourcePolicy("SmartIR", "MIT", LicenseStatus.APPROVED, attributionRequired = true),
        "probonopd-irdb" to SourcePolicy("probonopd/irdb", "CUSTOM", LicenseStatus.CONDITIONAL, attributionRequired = true),
        "global-cache" to SourcePolicy("Global Cache", "PROPRIETARY", LicenseStatus.BLOCKED, attributionRequired = false)
    )

    fun evaluate(sourceKey: String): SourcePolicy =
        POLICIES[sourceKey.lowercase()] ?: SourcePolicy(sourceKey, "UNKNOWN", LicenseStatus.CONDITIONAL, attributionRequired = true)
}

/**
 * Flipper `.ir` format parser implementation.
 */
class FlipperIrParser : IrSourceParser {
    override val formatName: String = "Flipper .ir"

    override fun parse(content: String, sourceArtifactId: String): ParseResult {
        val lines = content.lines()
        val signals = mutableListOf<ParsedIrSignal>()
        var currentName: String? = null
        var currentProtocol: String? = null
        var currentCarrier = 38000
        var currentRawData = mutableListOf<Int>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("name:") -> {
                    if (currentName != null && currentRawData.isNotEmpty()) {
                        val validPattern = currentRawData.filter { it > 0 }.toIntArray()
                        if (validPattern.isNotEmpty()) {
                            try {
                                val waveform = IrWaveform(currentCarrier, validPattern)
                                signals.add(
                                    ParsedIrSignal(
                                        name = currentName,
                                        carrierHz = currentCarrier,
                                        waveform = waveform,
                                        protocolName = currentProtocol
                                    )
                                )
                            } catch (_: Throwable) {}
                        }
                    }
                    currentName = trimmed.removePrefix("name:").trim()
                    currentRawData = mutableListOf()
                }
                trimmed.startsWith("protocol:") -> {
                    currentProtocol = trimmed.removePrefix("protocol:").trim()
                }
                trimmed.startsWith("frequency:") -> {
                    currentCarrier = trimmed.removePrefix("frequency:").trim().toIntOrNull() ?: 38000
                }
                trimmed.startsWith("data:") -> {
                    val numbers = trimmed.removePrefix("data:").trim().split(" ")
                    for (num in numbers) {
                        val v = num.toIntOrNull()
                        if (v != null && v > 0) {
                            currentRawData.add(v)
                        }
                    }
                }
            }
        }

        if (currentName != null && currentRawData.isNotEmpty()) {
            val validPattern = currentRawData.filter { it > 0 }.toIntArray()
            if (validPattern.isNotEmpty()) {
                try {
                    val waveform = IrWaveform(currentCarrier, validPattern)
                    signals.add(
                        ParsedIrSignal(
                            name = currentName,
                            carrierHz = currentCarrier,
                            waveform = waveform,
                            protocolName = currentProtocol
                        )
                    )
                } catch (_: Throwable) {}
            }
        }

        return if (signals.isNotEmpty()) ParseResult.Success(signals) else ParseResult.Error("No valid IR signals found in Flipper file")
    }
}
