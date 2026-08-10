package com.elysium369.meet.domain.visualdiagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehiclePowertrainTopologyTest {

    @Test
    fun plugInHybridIsResolvedBeforeGenericElectricTokens() {
        val topology = VehiclePowertrainTopologyResolver.resolve(
            engineDescription = "I4 turbo",
            fuelDescription = "plug-in hybrid electric PHEV",
            transmissionDescription = "e-CVT",
        )

        assertEquals(PowertrainElectrification.PHEV, topology.electrification.value)
        assertEquals(VoltageArchitecture.HIGH_VOLTAGE, topology.voltageArchitecture.value)
    }

    @Test
    fun displacementNumberIsNeverReinterpretedAsCylinderCount() {
        val topology = VehiclePowertrainTopologyResolver.resolve(
            engineDescription = "1600 cc DOHC",
            fuelDescription = "gasolina",
            transmissionDescription = "automático",
            displacementCc = 1600,
        )

        assertNull(topology.cylinderCount.value)
        assertEquals(1600, topology.displacementCc.value)
    }
}
