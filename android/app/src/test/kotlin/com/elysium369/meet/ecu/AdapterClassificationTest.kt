package com.elysium369.meet.ecu

import com.elysium369.meet.ecu.adapter.EcuAdapterClassification
import com.elysium369.meet.ecu.adapter.EcuAdapterClassifier
import com.elysium369.meet.ecu.adapter.EcuAdapterProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterClassificationTest {

    @Test
    fun `generic ELM327 clone is classified as read-only and denied from programming`() {
        val elmClone = EcuAdapterProfile(
            adapterId = "ADAPTER_ELM_01",
            deviceName = "OBDII Bluetooth ELM327 v1.5",
            transportType = "BLUETOOTH_CLASSIC",
            hardwareChipset = "PIC18F25K80_CLONE",
            firmwareVersion = "v1.5",
            supportsCanFd = false,
            supportsIsoTpFlowControlHardware = false,
            maxPacketBufferSize = 256,
            isVerifiedProgrammingHardware = false,
        )

        assertEquals(EcuAdapterClassification.OBD_READ_ONLY, elmClone.classification)
        assertFalse(
            "Generic ELM327 clone must NEVER be authorized for destructive programming",
            EcuAdapterClassifier.isAuthorizedForDestructiveProgramming(elmClone)
        )
    }

    @Test
    fun `tactrix openport J2534 pass-thru is classified as programming capable`() {
        val tactrix = EcuAdapterProfile(
            adapterId = "ADAPTER_TACTRIX_01",
            deviceName = "Tactrix OpenPort 2.0 J2534",
            transportType = "J2534",
            hardwareChipset = "ARM7_NXP_LPC2368",
            firmwareVersion = "v1.17.4877",
            supportsCanFd = false,
            supportsIsoTpFlowControlHardware = true,
            maxPacketBufferSize = 8192,
            isVerifiedProgrammingHardware = true,
        )

        assertEquals(EcuAdapterClassification.PROGRAMMING_CAPABLE, tactrix.classification)
        assertTrue(EcuAdapterClassifier.isAuthorizedForDestructiveProgramming(tactrix))
    }

    @Test
    fun `linux socketcan native interface is classified as programming capable`() {
        val socketCan = EcuAdapterProfile(
            adapterId = "ADAPTER_VCAN0",
            deviceName = "SocketCAN vcan0 / CANable",
            transportType = "SOCKETCAN",
            hardwareChipset = "SOCKETCAN_VIRTUAL",
            firmwareVersion = "Linux 6.x",
            supportsCanFd = true,
            supportsIsoTpFlowControlHardware = true,
            maxPacketBufferSize = 4096,
            isVerifiedProgrammingHardware = true,
        )

        assertEquals(EcuAdapterClassification.PROGRAMMING_CAPABLE, socketCan.classification)
        assertTrue(EcuAdapterClassifier.isAuthorizedForDestructiveProgramming(socketCan))
    }
}
