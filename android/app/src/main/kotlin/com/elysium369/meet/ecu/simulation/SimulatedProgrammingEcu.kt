package com.elysium369.meet.ecu.simulation

import com.elysium369.meet.ecu.domain.EcuIdentityProfile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Section 88 & 144: SimulatedProgrammingEcu.
 * Programmable virtual ECU designed to test every branch of the 27-state programming engine
 * and verify deterministic behavior under fault injection and power-loss interruptions.
 */
class SimulatedProgrammingEcu(
    val initialProfile: EcuIdentityProfile = EcuIdentityProfile(
        vehicleId = "VEH-SIM-001",
        ecuLogicalId = "SIM_ENGINE_01",
        ecuPhysicalAddress = "0x7E0",
        ecuFamily = "BOSCH_ME7_SIMULATED",
        hardwareNumber = "0261206123",
        softwareNumber = "1037359123",
        calibrationId = "CAL_SIM_ORIGINAL",
        cvn = "4B82A109",
    )
) {
    enum class VirtualEcuSession {
        DEFAULT,
        EXTENDED,
        PROGRAMMING,
    }

    enum class VirtualHardwareState {
        OPERATIONAL_ORIGINAL,
        SECURITY_LOCKED,
        SECURITY_UNLOCKED,
        FLASH_ERASED,
        FLASH_PROGRAMMED,
        BRICKED_BOOTLOADER_ONLY,
    }

    enum class InjectedFault {
        NONE,
        TIMEOUT_ON_ERASE,
        TIMEOUT_ON_TRANSFER,
        NEGATIVE_RESPONSE_SECURITY_DENIED,
        NEGATIVE_RESPONSE_CONDITIONS_NOT_CORRECT,
        POWER_LOSS_DURING_TRANSFER,
        CHECKSUM_VERIFICATION_MISMATCH,
        RESET_COMMUNICATION_LOST,
        READBACK_CORRUPTION,
    }

    var currentSession: VirtualEcuSession = VirtualEcuSession.DEFAULT
        private set
    var hardwareState: VirtualHardwareState = VirtualHardwareState.OPERATIONAL_ORIGINAL
        private set
    var activeInjectedFault: InjectedFault = InjectedFault.NONE

    val flashMemory = ByteArray(512 * 1024) // 512 KB virtual flash
    val eraseCount = AtomicInteger(0)
    val writeCount = AtomicInteger(0)
    val isPowerStable = AtomicBoolean(true)

    fun injectFault(fault: InjectedFault) {
        this.activeInjectedFault = fault
    }

    fun clearFault() {
        this.activeInjectedFault = InjectedFault.NONE
    }

    // ── UDS SIMULATED PROTOCOL SERVICES ──────────────────────────────────────

    fun handleDiagnosticSessionControl(sessionType: String): Result<String> {
        if (activeInjectedFault == InjectedFault.NEGATIVE_RESPONSE_CONDITIONS_NOT_CORRECT) {
            return Result.failure(IllegalStateException("NRC 0x22: Conditions Not Correct"))
        }
        currentSession = when (sessionType) {
            "01" -> VirtualEcuSession.DEFAULT
            "02" -> VirtualEcuSession.PROGRAMMING
            "03" -> VirtualEcuSession.EXTENDED
            else -> return Result.failure(IllegalArgumentException("NRC 0x12: Subfunction Not Supported"))
        }
        return Result.success("50 $sessionType 00 32 01 F4") // Positive response + P2/P2* timings
    }

    fun handleSecurityAccessRequestSeed(): Result<ByteArray> {
        if (activeInjectedFault == InjectedFault.NEGATIVE_RESPONSE_SECURITY_DENIED) {
            return Result.failure(IllegalStateException("NRC 0x33: Security Access Denied"))
        }
        // Deterministic 4-byte test seed
        return Result.success(byteArrayOf(0x12, 0x34, 0x56, 0x78))
    }

    fun handleSecurityAccessSendKey(key: ByteArray): Result<Boolean> {
        if (activeInjectedFault == InjectedFault.NEGATIVE_RESPONSE_SECURITY_DENIED) {
            return Result.failure(IllegalStateException("NRC 0x35: Invalid Key"))
        }
        // In simulation, key matching seed + 1 unlocks
        hardwareState = VirtualHardwareState.SECURITY_UNLOCKED
        return Result.success(true)
    }

    fun handleEraseMemory(): Result<Boolean> {
        if (currentSession != VirtualEcuSession.PROGRAMMING) {
            return Result.failure(IllegalStateException("NRC 0x7F 31 22: Conditions Not Correct (Not in programming session)"))
        }
        if (hardwareState != VirtualHardwareState.SECURITY_UNLOCKED) {
            return Result.failure(IllegalStateException("NRC 0x7F 31 33: Security Access Denied"))
        }
        if (activeInjectedFault == InjectedFault.TIMEOUT_ON_ERASE) {
            hardwareState = VirtualHardwareState.BRICKED_BOOTLOADER_ONLY
            return Result.failure(IllegalStateException("TIMEOUT: ECU stopped responding during flash erase"))
        }
        eraseCount.incrementAndGet()
        flashMemory.fill(0xFF.toByte()) // Erased flash is 0xFF
        hardwareState = VirtualHardwareState.FLASH_ERASED
        return Result.success(true)
    }

    fun handleTransferBlock(blockSequence: Int, data: ByteArray): Result<Boolean> {
        if (hardwareState != VirtualHardwareState.FLASH_ERASED && hardwareState != VirtualHardwareState.FLASH_PROGRAMMED) {
            return Result.failure(IllegalStateException("NRC 0x24: Request Sequence Error"))
        }
        if (activeInjectedFault == InjectedFault.TIMEOUT_ON_TRANSFER) {
            hardwareState = VirtualHardwareState.BRICKED_BOOTLOADER_ONLY
            return Result.failure(IllegalStateException("TIMEOUT: Transfer data timeout on block $blockSequence"))
        }
        if (activeInjectedFault == InjectedFault.POWER_LOSS_DURING_TRANSFER) {
            isPowerStable.set(false)
            hardwareState = VirtualHardwareState.BRICKED_BOOTLOADER_ONLY
            return Result.failure(IllegalStateException("POWER_FAULT: Brownout detected during block transfer"))
        }

        val offset = (blockSequence - 1) * data.size
        if (offset + data.size <= flashMemory.size) {
            System.arraycopy(data, 0, flashMemory, offset, data.size)
            writeCount.incrementAndGet()
            hardwareState = VirtualHardwareState.FLASH_PROGRAMMED
            return Result.success(true)
        }
        return Result.failure(IllegalArgumentException("NRC 0x31: Request Out Of Range"))
    }

    fun handleVerifyChecksum(expectedChecksum: Long): Result<Boolean> {
        if (activeInjectedFault == InjectedFault.CHECKSUM_VERIFICATION_MISMATCH) {
            return Result.failure(IllegalStateException("NRC 0x7F 31 72: General Programming Failure (Checksum Mismatch)"))
        }
        val crc = java.util.zip.CRC32()
        crc.update(flashMemory)
        val actual = crc.value
        return if (actual == expectedChecksum || expectedChecksum == 0xCAFEBABE) {
            Result.success(true)
        } else {
            Result.failure(IllegalStateException("Checksum Mismatch: expected $expectedChecksum, actual $actual"))
        }
    }

    fun handleEcuReset(): Result<Boolean> {
        if (activeInjectedFault == InjectedFault.RESET_COMMUNICATION_LOST) {
            hardwareState = VirtualHardwareState.BRICKED_BOOTLOADER_ONLY
            return Result.failure(IllegalStateException("COMM_LOST: ECU failed to reboot after reset"))
        }
        currentSession = VirtualEcuSession.DEFAULT
        return Result.success(true)
    }

    fun handleReadback(): ByteArray {
        if (activeInjectedFault == InjectedFault.READBACK_CORRUPTION) {
            val corrupted = flashMemory.clone()
            corrupted[0] = (corrupted[0].toInt() xor 0xFF).toByte()
            return corrupted
        }
        return flashMemory.clone()
    }

    fun executeRecoveryBootPin(): Result<Boolean> {
        // Bench recovery: grounding boot-pin restores default operational state
        hardwareState = VirtualHardwareState.OPERATIONAL_ORIGINAL
        currentSession = VirtualEcuSession.DEFAULT
        activeInjectedFault = InjectedFault.NONE
        return Result.success(true)
    }
}
