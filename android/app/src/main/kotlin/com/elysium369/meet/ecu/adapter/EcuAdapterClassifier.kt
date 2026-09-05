package com.elysium369.meet.ecu.adapter

/**
 * Section 12: Programming-Capable Adapter Model.
 *
 * DOCTRINE:
 * ELM327 detected is NOT PROGRAMMING_CAPABLE.
 * A generic Bluetooth ELM clone must NEVER become a universal programmer
 * simply because arbitrary bytes can be emitted.
 */

enum class EcuAdapterClassification {
    OBD_READ_ONLY,
    DIAGNOSTIC_READ_WRITE,
    ACTIVE_DIAGNOSTIC,
    PROGRAMMING_CAPABLE,
    BENCH_PROGRAMMER,
    UNKNOWN,
}

data class EcuAdapterProfile(
    val adapterId: String,
    val deviceName: String,
    val transportType: String, // "BLUETOOTH_CLASSIC", "BLE", "WIFI", "USB", "J2534", "SOCKETCAN"
    val hardwareChipset: String,
    val firmwareVersion: String,
    val supportsCanFd: Boolean = false,
    val supportsIsoTpFlowControlHardware: Boolean = false,
    val maxPacketBufferSize: Int = 256,
    val isVerifiedProgrammingHardware: Boolean = false,
) {
    val classification: EcuAdapterClassification
        get() = EcuAdapterClassifier.classify(this)
}

object EcuAdapterClassifier {

    fun classify(profile: EcuAdapterProfile): EcuAdapterClassification {
        val name = profile.deviceName.uppercase()
        val chip = profile.hardwareChipset.uppercase()
        val transport = profile.transportType.uppercase()

        // 1. Dedicated Bench / Hardware Programmers
        if (transport == "J2534" || name.contains("TACTRIX") || name.contains("MONGOOSE") || name.contains("KVASER")) {
            return EcuAdapterClassification.PROGRAMMING_CAPABLE
        }

        if (transport == "SOCKETCAN" || chip.contains("SOCKETCAN") || name.contains("PANDA")) {
            return EcuAdapterClassification.PROGRAMMING_CAPABLE
        }

        if (name.contains("BDM") || name.contains("BOOTLOADER") || name.contains("BENCH_RIG")) {
            return EcuAdapterClassification.BENCH_PROGRAMMER
        }

        // 2. High-speed Diagnostic adapters (STN, OBDLink, vLinker)
        if (chip.contains("STN") || name.contains("OBDLINK") || name.contains("VLINKER")) {
            return if (profile.supportsIsoTpFlowControlHardware && profile.maxPacketBufferSize >= 2048 && profile.isVerifiedProgrammingHardware) {
                EcuAdapterClassification.PROGRAMMING_CAPABLE
            } else {
                EcuAdapterClassification.ACTIVE_DIAGNOSTIC
            }
        }

        // 3. Generic ELM327 and generic BLE dongles are strictly READ-ONLY or standard diagnostic
        if (chip.contains("ELM327") || name.contains("ELM327") || name.contains("OBDII") || transport == "BLE") {
            return EcuAdapterClassification.OBD_READ_ONLY
        }

        return EcuAdapterClassification.UNKNOWN
    }

    fun isAuthorizedForDestructiveProgramming(profile: EcuAdapterProfile): Boolean {
        val c = classify(profile)
        return c == EcuAdapterClassification.PROGRAMMING_CAPABLE || c == EcuAdapterClassification.BENCH_PROGRAMMER
    }
}
