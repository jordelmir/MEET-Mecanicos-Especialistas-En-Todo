package com.elysium369.meet.ecu

import com.elysium369.meet.ecu.asam.A2lAxisDescription
import com.elysium369.meet.ecu.asam.A2lCalibrationModel
import com.elysium369.meet.ecu.asam.A2lCharacteristic
import com.elysium369.meet.ecu.asam.A2lCharacteristicType
import com.elysium369.meet.ecu.asam.A2lComputationMethod
import com.elysium369.meet.ecu.asam.OdxDiagnosticModel
import com.elysium369.meet.ecu.asam.OdxParameter
import com.elysium369.meet.ecu.asam.OdxService
import com.elysium369.meet.ecu.asam.OdxVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdxAndA2lParserTest {

    @Test
    fun `ODX model captures services and flags write or state altering procedures`() {
        val readVinService = OdxService(
            id = "SRV_READ_VIN",
            shortName = "ReadVin",
            serviceIdHex = "22",
            requestParameters = listOf(OdxParameter("DID", "DataIdentifier", 0, 16)),
            positiveResponseServiceIdHex = "62",
        )

        val eraseService = OdxService(
            id = "SRV_ERASE_FLASH",
            shortName = "EraseFlash",
            serviceIdHex = "31",
            requestParameters = listOf(OdxParameter("RoutineType", "RoutineControlOption", 0, 8)),
            positiveResponseServiceIdHex = "71",
            isRoutineControl = true,
            isMemoryOperation = true,
        )

        val variant = OdxVariant(
            variantId = "VAR_ME7_18T",
            shortName = "Bosch_ME7_18T",
            ecuHardwareNumber = "0261206123",
            ecuSoftwareNumber = "1037359123",
            services = listOf(readVinService, eraseService),
        )

        val model = OdxDiagnosticModel(
            modelId = "ODX-VAG-ME7",
            schemaVersion = "2.2.0",
            manufacturer = "Volkswagen AG",
            variants = listOf(variant),
            sha256SourceHash = "0".repeat(64),
        )

        assertFalse("Read VIN is not state altering", readVinService.isWriteOrStateAltering)
        assertTrue("Erase flash is strictly state altering", eraseService.isWriteOrStateAltering)
        assertEquals(1, model.variants.size)
    }

    @Test
    fun `A2L calibration model defines 3D maps and curves with dimension verification`() {
        val rpmAxis = A2lAxisDescription("N", "Engine Speed", "COMP_RPM", 16, 800.0, 7000.0)
        val loadAxis = A2lAxisDescription("RL", "Engine Load", "COMP_LOAD", 12, 10.0, 190.0)

        val kfzwMap = A2lCharacteristic(
            identifier = "KFZW",
            longIdentifier = "Ignition Timing Advance Map",
            type = A2lCharacteristicType.MAP,
            address = 0x12000L,
            recordLayoutName = "UBYTE_ROW_DIR",
            computationMethodName = "COMP_IGN_DEGREES",
            lowerLimit = -10.0,
            upperLimit = 45.0,
            axes = listOf(rpmAxis, loadAxis),
        )

        val model = A2lCalibrationModel(
            modelId = "A2L-ME7-18T-AWT",
            projectName = "ME7.5_AWT_150HP",
            ecuHardwareNumber = "0261206123",
            epkSoftwareVersion = "1037359123",
            characteristics = listOf(kfzwMap),
            computationMethods = mapOf(
                "COMP_IGN_DEGREES" to A2lComputationMethod("COMP_IGN_DEGREES", "0.75 * x - 10", "deg CA")
            ),
            sourceHash = "1".repeat(64),
        )

        val found = model.findCharacteristic("KFZW")
        assertNotNull(found)
        assertEquals(2, found!!.axes.size)
        assertEquals(A2lCharacteristicType.MAP, found.type)
        assertEquals(0x12000L, found.address)
    }
}
