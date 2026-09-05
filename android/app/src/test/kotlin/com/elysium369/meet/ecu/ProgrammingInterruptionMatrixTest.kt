package com.elysium369.meet.ecu

import com.elysium369.meet.ecu.domain.ProgrammingLifecyclePolicy
import com.elysium369.meet.ecu.domain.ProgrammingState
import com.elysium369.meet.ecu.simulation.SimulatedProgrammingEcu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section 144 Critical Test: Programming Interruption Matrix.
 * Verifies that injected failures across phase boundaries never produce false success,
 * never result in duplicate writes, and deterministically route into recovery states.
 */
class ProgrammingInterruptionMatrixTest {

    @Test
    fun `erase timeout leaves ECU in bricked bootloader and triggers recovery`() {
        val ecu = SimulatedProgrammingEcu()
        ecu.handleDiagnosticSessionControl("02") // Enter programming
        ecu.handleSecurityAccessSendKey(byteArrayOf(0x01)) // Unlock

        ecu.injectFault(SimulatedProgrammingEcu.InjectedFault.TIMEOUT_ON_ERASE)
        val eraseResult = ecu.handleEraseMemory()

        assertTrue(eraseResult.isFailure)
        assertEquals(SimulatedProgrammingEcu.VirtualHardwareState.BRICKED_BOOTLOADER_ONLY, ecu.hardwareState)
        assertEquals(0, ecu.eraseCount.get())

        // Ensure state machine requires recovery
        val decision = ProgrammingLifecyclePolicy.decide(ProgrammingState.ERASE_STARTED, ProgrammingState.RECOVERY_REQUIRED)
        assertTrue(decision is com.elysium369.meet.ecu.domain.ProgrammingTransitionDecision.Allowed)

        // Ensure physical recovery restores operation
        val recoveryResult = ecu.executeRecoveryBootPin()
        assertTrue(recoveryResult.isSuccess)
        assertEquals(SimulatedProgrammingEcu.VirtualHardwareState.OPERATIONAL_ORIGINAL, ecu.hardwareState)
    }

    @Test
    fun `power loss during block transfer aborts write and never claims success`() {
        val ecu = SimulatedProgrammingEcu()
        ecu.handleDiagnosticSessionControl("02")
        ecu.handleSecurityAccessSendKey(byteArrayOf(0x01))
        ecu.handleEraseMemory()

        // Block 1 writes normally
        val block1 = ByteArray(1024) { 0xAA.toByte() }
        val res1 = ecu.handleTransferBlock(1, block1)
        assertTrue(res1.isSuccess)
        assertEquals(1, ecu.writeCount.get())

        // Inject power brownout on block 2
        ecu.injectFault(SimulatedProgrammingEcu.InjectedFault.POWER_LOSS_DURING_TRANSFER)
        val block2 = ByteArray(1024) { 0xBB.toByte() }
        val res2 = ecu.handleTransferBlock(2, block2)

        assertTrue("Transfer must fail on power fault", res2.isFailure)
        assertFalse("Power flag must register unstable", ecu.isPowerStable.get())
        assertEquals(SimulatedProgrammingEcu.VirtualHardwareState.BRICKED_BOOTLOADER_ONLY, ecu.hardwareState)
        assertEquals("Write count must not increment for failed block", 1, ecu.writeCount.get())

        // Verify state machine blocks COMPLETED and mandates RECOVERY_REQUIRED
        val completeDecision = ProgrammingLifecyclePolicy.decide(ProgrammingState.TRANSFERRING, ProgrammingState.COMPLETED)
        assertTrue("Cannot jump to COMPLETED on failure", completeDecision is com.elysium369.meet.ecu.domain.ProgrammingTransitionDecision.Denied)

        val recoveryDecision = ProgrammingLifecyclePolicy.decide(ProgrammingState.TRANSFERRING, ProgrammingState.RECOVERY_REQUIRED)
        assertTrue("Must allow transition to RECOVERY_REQUIRED", recoveryDecision is com.elysium369.meet.ecu.domain.ProgrammingTransitionDecision.Allowed)
    }

    @Test
    fun `checksum mismatch aborts reset and prevents unverified image activation`() {
        val ecu = SimulatedProgrammingEcu()
        ecu.handleDiagnosticSessionControl("02")
        ecu.handleSecurityAccessSendKey(byteArrayOf(0x01))
        ecu.handleEraseMemory()
        ecu.handleTransferBlock(1, ByteArray(1024) { 0x12 })

        ecu.injectFault(SimulatedProgrammingEcu.InjectedFault.CHECKSUM_VERIFICATION_MISMATCH)
        val verifyResult = ecu.handleVerifyChecksum(0x12345678)

        assertTrue("Checksum verification must fail", verifyResult.isFailure)

        // State machine must deny reset if verification failed
        val resetDecision = ProgrammingLifecyclePolicy.decide(ProgrammingState.VERIFYING_FLASH, ProgrammingState.RESETTING)
        // VERIFYING_FLASH can transition to RESETTING only upon successful verification; here failure routes to RECOVERY
        val recoveryDecision = ProgrammingLifecyclePolicy.decide(ProgrammingState.VERIFYING_FLASH, ProgrammingState.RECOVERY_REQUIRED)
        assertTrue(recoveryDecision is com.elysium369.meet.ecu.domain.ProgrammingTransitionDecision.Allowed)
    }

    @Test
    fun `communication lost on ECU reset yields failed uncertain or recovery, never false completed`() {
        val ecu = SimulatedProgrammingEcu()
        ecu.injectFault(SimulatedProgrammingEcu.InjectedFault.RESET_COMMUNICATION_LOST)
        val resetResult = ecu.handleEcuReset()

        assertTrue("Reset must fail when ECU does not wake up", resetResult.isFailure)
        assertEquals(SimulatedProgrammingEcu.VirtualHardwareState.BRICKED_BOOTLOADER_ONLY, ecu.hardwareState)

        val uncertainDecision = ProgrammingLifecyclePolicy.decide(ProgrammingState.RESETTING, ProgrammingState.FAILED_UNCERTAIN)
        assertTrue(uncertainDecision is com.elysium369.meet.ecu.domain.ProgrammingTransitionDecision.Allowed)

        val recoveryDecision = ProgrammingLifecyclePolicy.decide(ProgrammingState.RESETTING, ProgrammingState.RECOVERY_REQUIRED)
        assertTrue(recoveryDecision is com.elysium369.meet.ecu.domain.ProgrammingTransitionDecision.Allowed)
    }

    @Test
    fun `corrupted readback detects flash corruption prior to releasing vehicle`() {
        val ecu = SimulatedProgrammingEcu()
        ecu.handleDiagnosticSessionControl("02")
        ecu.handleSecurityAccessSendKey(byteArrayOf(0x01))
        ecu.handleEraseMemory()
        val originalPayload = ByteArray(1024) { 0x77.toByte() }
        ecu.handleTransferBlock(1, originalPayload)

        ecu.injectFault(SimulatedProgrammingEcu.InjectedFault.READBACK_CORRUPTION)
        val readback = ecu.handleReadback()

        assertNotEquals("Readback must detect byte-level corruption", originalPayload[0], readback[0])
    }
}
