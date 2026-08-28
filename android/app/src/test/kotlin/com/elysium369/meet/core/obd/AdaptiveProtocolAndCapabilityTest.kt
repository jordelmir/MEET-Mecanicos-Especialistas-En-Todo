package com.elysium369.meet.core.obd

import org.junit.Assert.*
import org.junit.Test

class AdaptiveProtocolAndCapabilityTest {

    @Test
    fun adapterRiskClassification() {
        assertEquals(AdapterRiskTier.GENUINE_STN, AdaptiveProtocolNegotiatorV2.evaluateAdapterRisk("STN2120 v5.1.0"))
        assertEquals(AdapterRiskTier.GENUINE_OBDLINK, AdaptiveProtocolNegotiatorV2.evaluateAdapterRisk("OBDLink MX+ Bluetooth"))
        assertEquals(AdapterRiskTier.GENUINE_CANDLELIGHT, AdaptiveProtocolNegotiatorV2.evaluateAdapterRisk("gs_usb candlelight firmware"))
        assertEquals(AdapterRiskTier.COMPATIBLE_V15, AdaptiveProtocolNegotiatorV2.evaluateAdapterRisk("ELM327 v1.5"))
        assertEquals(AdapterRiskTier.DEFECTIVE_CLONE_RISK_HIGH, AdaptiveProtocolNegotiatorV2.evaluateAdapterRisk("ELM327 v2.1"))
    }

    @Test
    fun planCompilationForModernVehicle() {
        val plan = AdaptiveProtocolNegotiatorV2.compilePlan(
            adapterVersionString = "STN1170 v4.2",
            cachedSuccessfulProtocol = null,
            vehicleYear = 2021
        )

        assertEquals(AdapterRiskTier.GENUINE_STN, plan.adapterRiskTier)
        assertEquals(DiagnosticProbeSpeed.OPTIMIZED_STANDARD, plan.probeSpeed)
        assertEquals("ISO_15765_4_CAN_11BIT_500K", plan.preferredCandidate.protocolId)
        assertEquals(0L, plan.interCommandDelayMs)
        assertTrue(plan.enableAdaptiveTiming)
    }

    @Test
    fun planCompilationForDefectiveCloneAddsDelaysAndDisablesATAT() {
        val plan = AdaptiveProtocolNegotiatorV2.compilePlan(
            adapterVersionString = "ELM327 v2.1 Chinese Clone",
            cachedSuccessfulProtocol = null,
            vehicleYear = 2018
        )

        assertEquals(AdapterRiskTier.DEFECTIVE_CLONE_RISK_HIGH, plan.adapterRiskTier)
        assertEquals(DiagnosticProbeSpeed.EXHAUSTIVE_SAFE_PROBE, plan.probeSpeed)
        assertEquals(50L, plan.interCommandDelayMs)
        assertFalse(plan.enableAdaptiveTiming)
    }

    @Test
    fun vehicleCapabilityPackMatchingAndIntegrity() {
        val toyotaPack = VehicleCapabilityPackRegistry.TOYOTA_HYBRID_PACK
        assertTrue(toyotaPack.verifyIntegrity())
        assertTrue(toyotaPack.matchesVehicle("Toyota", "Prius", 2017))
        assertFalse(toyotaPack.matchesVehicle("Toyota", "Prius", 2005))
        assertFalse(toyotaPack.matchesVehicle("Honda", "Civic", 2017))

        val vagPack = VehicleCapabilityPackRegistry.VAG_TSI_PACK
        assertTrue(vagPack.verifyIntegrity())
        assertTrue(vagPack.matchesVehicle("Volkswagen", "Golf", 2019))
        assertFalse(vagPack.matchesVehicle("Volkswagen", "Golf", 2008))
    }
}
