package com.elysium369.meet.core.obd.model

import com.elysium369.meet.ecu.adapter.EcuAdapterClassification
import kotlinx.serialization.Serializable

/**
 * Adapter Hardware Family classification for automotive OBD-II interfaces.
 */
@Serializable
enum class AdapterChipFamily {
    OBDLINK_STN,
    VLINKER,
    ELM327_GENUINE,
    ELM327_CLONE,
    UNKNOWN;

    val displayName: String
        get() = when (this) {
            OBDLINK_STN -> "OBDLink STN Architecture"
            VLINKER -> "vLinker High-Speed Series"
            ELM327_GENUINE -> "ELM327 Genuine Firmware"
            ELM327_CLONE -> "ELM327 Clone / Budget Dongle"
            UNKNOWN -> "Generic OBD Adapter"
        }
}

/**
 * Specification and operational capabilities of an OBD adapter profile.
 */
@Serializable
data class ObdAdapterCapabilityProfile(
    val family: AdapterChipFamily,
    val chipset: String,
    val firmwareVersion: String,
    val supportsCanFd: Boolean,
    val supportsIsoTp: Boolean,
    val maxPacketBufferSize: Int,
    val optimalBaudRate: Int,
    val defaultCommandDelayMs: Long,
    val recommendedInitCommands: List<String>,
    val classification: EcuAdapterClassification,
    val requiresAdaptiveTimingDisabled: Boolean = false,
    val isClone: Boolean = family == AdapterChipFamily.ELM327_CLONE,
)

/**
 * ObdAdapterMatrix — Hardware profile registry and classification matrix
 * for ELM327, vLinker, and OBDLink (STN) adapters.
 */
object ObdAdapterMatrix {

    // Reference Catalog Profiles
    val OBDLINK_MX_PLUS = ObdAdapterCapabilityProfile(
        family = AdapterChipFamily.OBDLINK_STN,
        chipset = "STN2120",
        firmwareVersion = "v5.x",
        supportsCanFd = true,
        supportsIsoTp = true,
        maxPacketBufferSize = 4096,
        optimalBaudRate = 500000,
        defaultCommandDelayMs = 15L,
        recommendedInitCommands = listOf("ST AT 1", "STP31", "STPBR 1", "ATAT1", "ATCAF1", "ATAL"),
        classification = EcuAdapterClassification.ACTIVE_DIAGNOSTIC,
    )

    val OBDLINK_EX = ObdAdapterCapabilityProfile(
        family = AdapterChipFamily.OBDLINK_STN,
        chipset = "STN1170",
        firmwareVersion = "v5.x",
        supportsCanFd = true,
        supportsIsoTp = true,
        maxPacketBufferSize = 4096,
        optimalBaudRate = 2000000,
        defaultCommandDelayMs = 10L,
        recommendedInitCommands = listOf("ST AT 1", "STP31", "STPBR 1", "ATAT1", "ATCAF1", "ATAL"),
        classification = EcuAdapterClassification.PROGRAMMING_CAPABLE,
    )

    val VLINKER_FD_PLUS = ObdAdapterCapabilityProfile(
        family = AdapterChipFamily.VLINKER,
        chipset = "MIC3322",
        firmwareVersion = "v2.2+",
        supportsCanFd = true,
        supportsIsoTp = true,
        maxPacketBufferSize = 4096,
        optimalBaudRate = 500000,
        defaultCommandDelayMs = 20L,
        recommendedInitCommands = listOf("ST AT 1", "STP31", "ATAT1", "ATCAF1", "ATAL"),
        classification = EcuAdapterClassification.ACTIVE_DIAGNOSTIC,
    )

    val VLINKER_MC_PLUS = ObdAdapterCapabilityProfile(
        family = AdapterChipFamily.VLINKER,
        chipset = "MIC3322-MC",
        firmwareVersion = "v2.2+",
        supportsCanFd = false,
        supportsIsoTp = true,
        maxPacketBufferSize = 2048,
        optimalBaudRate = 500000,
        defaultCommandDelayMs = 20L,
        recommendedInitCommands = listOf("ST AT 1", "STP31", "ATAT1", "ATCAF1", "ATAL"),
        classification = EcuAdapterClassification.ACTIVE_DIAGNOSTIC,
    )

    val ELM327_GENUINE_V22 = ObdAdapterCapabilityProfile(
        family = AdapterChipFamily.ELM327_GENUINE,
        chipset = "PIC18F2480",
        firmwareVersion = "ELM327 v2.2",
        supportsCanFd = false,
        supportsIsoTp = false,
        maxPacketBufferSize = 512,
        optimalBaudRate = 38400,
        defaultCommandDelayMs = 35L,
        recommendedInitCommands = listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATCAF1", "ATAT1", "ATAL"),
        classification = EcuAdapterClassification.DIAGNOSTIC_READ_WRITE,
    )

    val ELM327_CLONE_V15 = ObdAdapterCapabilityProfile(
        family = AdapterChipFamily.ELM327_CLONE,
        chipset = "PIC18F25K80-CLONE",
        firmwareVersion = "ELM327 v1.5",
        supportsCanFd = false,
        supportsIsoTp = false,
        maxPacketBufferSize = 256,
        optimalBaudRate = 38400,
        defaultCommandDelayMs = 60L,
        recommendedInitCommands = listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATCAF1", "ATAT1", "ATAL", "ATST64"),
        classification = EcuAdapterClassification.OBD_READ_ONLY,
    )

    val ELM327_CLONE_FAKE_V21 = ObdAdapterCapabilityProfile(
        family = AdapterChipFamily.ELM327_CLONE,
        chipset = "BK3231-CLONE",
        firmwareVersion = "ELM327 v2.1 (Clone)",
        supportsCanFd = false,
        supportsIsoTp = false,
        maxPacketBufferSize = 64,
        optimalBaudRate = 38400,
        defaultCommandDelayMs = 80L,
        recommendedInitCommands = listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATCAF1", "ATAT0", "ATST96"),
        classification = EcuAdapterClassification.OBD_READ_ONLY,
        requiresAdaptiveTimingDisabled = true,
    )

    val GENERIC_FALLBACK = ObdAdapterCapabilityProfile(
        family = AdapterChipFamily.UNKNOWN,
        chipset = "GENERIC_CHIP",
        firmwareVersion = "UNKNOWN",
        supportsCanFd = false,
        supportsIsoTp = false,
        maxPacketBufferSize = 128,
        optimalBaudRate = 38400,
        defaultCommandDelayMs = 70L,
        recommendedInitCommands = listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATCAF1", "ATAT0", "ATST96"),
        classification = EcuAdapterClassification.UNKNOWN,
    )

    /**
     * Determines adapter family based on version string (ATI), device name, and STN identity (STI).
     */
    fun detectFamily(
        chipVersion: String,
        deviceName: String = "",
        stiResponse: String = ""
    ): AdapterChipFamily {
        val chip = chipVersion.uppercase()
        val name = deviceName.uppercase()
        val sti = stiResponse.uppercase()

        // 1. vLinker Detection (vLinker adapters respond to STI with STN-compatible strings like "STN vLinker FD")
        if (name.contains("VLINKER") || chip.contains("VLINKER") || sti.contains("VLINKER")) {
            return AdapterChipFamily.VLINKER
        }

        // 2. OBDLink / STN Detection
        if (sti.contains("STN") || chip.contains("STN") || name.contains("OBDLINK")) {
            return AdapterChipFamily.OBDLINK_STN
        }

        // 3. Genuine ELM Detection
        // Genuine ELM Electronics produces v1.3a, v1.4b, and v2.2+ with specific banners and no clone quirks
        if ((chip.contains("ELM327 V2.2") || chip.contains("ELM327 V2.3")) &&
            !name.contains("IOS-VLINK") && !name.contains("OBDII")
        ) {
            return AdapterChipFamily.ELM327_GENUINE
        }

        // 4. Clone Detection (v1.5, fake v2.1, generic Bluetooth dongles)
        if (chip.contains("ELM327") || chip.contains("V1.5") || chip.contains("V2.1") ||
            name.contains("OBD") || name.contains("VGATE") || name.contains("KONNWEI")
        ) {
            return AdapterChipFamily.ELM327_CLONE
        }

        return AdapterChipFamily.UNKNOWN
    }

    /**
     * Resolves the full capability profile from adapter identification data.
     */
    fun resolveProfile(
        chipVersion: String,
        deviceName: String = "",
        stiResponse: String = ""
    ): ObdAdapterCapabilityProfile {
        val family = detectFamily(chipVersion, deviceName, stiResponse)
        val name = deviceName.uppercase()
        val chip = chipVersion.uppercase()

        return when (family) {
            AdapterChipFamily.OBDLINK_STN -> {
                if (name.contains("EX") || name.contains("SX")) {
                    OBDLINK_EX.copy(chipset = parseStnChipset(stiResponse).ifBlank { "STN1170" })
                } else {
                    OBDLINK_MX_PLUS.copy(chipset = parseStnChipset(stiResponse).ifBlank { "STN2120" })
                }
            }
            AdapterChipFamily.VLINKER -> {
                val isFd = name.contains("FD") || chip.contains("FD")
                if (isFd) {
                    VLINKER_FD_PLUS
                } else {
                    VLINKER_MC_PLUS
                }
            }
            AdapterChipFamily.ELM327_GENUINE -> ELM327_GENUINE_V22
            AdapterChipFamily.ELM327_CLONE -> {
                if (chip.contains("2.1") || name.contains("2.1")) {
                    ELM327_CLONE_FAKE_V21
                } else {
                    ELM327_CLONE_V15
                }
            }
            AdapterChipFamily.UNKNOWN -> GENERIC_FALLBACK
        }
    }

    private fun parseStnChipset(sti: String): String {
        val match = Regex("(STN\\d{4})", RegexOption.IGNORE_CASE).find(sti)
        return match?.groupValues?.getOrNull(1)?.uppercase() ?: ""
    }
}
