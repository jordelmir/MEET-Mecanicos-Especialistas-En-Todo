package com.elysium369.meet.visualdiagnostics

import com.elysium369.meet.data.visualdiagnostics.VisualDiagnosticRepositoryImpl
import com.elysium369.meet.domain.visualdiagnostics.BomSystem
import com.elysium369.meet.domain.visualdiagnostics.ComponentVerificationLevel
import com.elysium369.meet.domain.visualdiagnostics.EngineType
import com.elysium369.meet.domain.visualdiagnostics.VisualBomAtlas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualBomAtlasTest {

    @Test
    fun p0230MapsToFuelPumpCircuitWithoutClaimingExactCompatibility() {
        val p0230Nodes = VisualBomAtlas.byDtc("P0230")
        val circuit = p0230Nodes.firstOrNull { it.id == "fuel_pump_circuit" }
        val relay = p0230Nodes.firstOrNull { it.id == "fuel_pump_relay" }

        assertNotNull(circuit)
        assertNotNull(relay)
        assertEquals(ComponentVerificationLevel.GENERIC_REPRESENTATION, circuit!!.verificationLevel)
        assertTrue(circuit.componentIds.contains("fuel_pump"))
        assertTrue(circuit.componentIds.contains("relay_fuel_pump"))
        assertTrue(circuit.componentIds.contains("fuse_fuel_pump"))
        assertTrue(circuit.evidenceRequirements.any { it.contains("manometro", ignoreCase = true) })
        assertTrue(circuit.exactnessDisclaimer.contains("VIN/OEM/foto/manual"))
    }

    @Test
    fun repositoryResolvesBomNodeToExistingL4Components() {
        val repository = VisualDiagnosticRepositoryImpl()
        val components = repository.componentsForBomNode(EngineType.L4, "fuel_pump_circuit")
        val ids = components.map { it.id }.toSet()

        assertTrue(ids.contains("fuel_pump"))
        assertTrue(ids.contains("relay_fuel_pump"))
        assertTrue(ids.contains("fuse_fuel_pump"))
        assertFalse(ids.contains("alternator"))
    }

    @Test
    fun atlasCoversCriticalSafetyFamiliesFor3dNavigation() {
        val systems = VisualBomAtlas.nodes().map { it.system }.toSet()

        assertTrue(systems.contains(BomSystem.ENGINE))
        assertTrue(systems.contains(BomSystem.ELECTRICAL))
        assertTrue(systems.contains(BomSystem.BRAKES))
        assertTrue(systems.contains(BomSystem.HYBRID_EV))
        assertTrue(VisualBomAtlas.nodes().any { it.safetyCritical })
    }
}
