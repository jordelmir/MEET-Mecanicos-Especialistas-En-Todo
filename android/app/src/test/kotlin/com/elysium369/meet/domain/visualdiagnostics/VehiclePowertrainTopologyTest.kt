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

        assertEquals(PowertrainElectrification.PHEV, topology.electrification.valueOrNull)
        assertEquals(VoltageArchitecture.HIGH_VOLTAGE, topology.voltageArchitecture.valueOrNull)
    }

    @Test
    fun displacementNumberIsNeverReinterpretedAsCylinderCount() {
        val topology = VehiclePowertrainTopologyResolver.resolve(
            engineDescription = "1600 cc DOHC",
            fuelDescription = "gasolina",
            transmissionDescription = "automático",
            displacementCc = 1600,
        )

        assertNull(topology.cylinderCount.valueOrNull)
        assertEquals(1600, topology.displacementCc.valueOrNull)
    }
}
