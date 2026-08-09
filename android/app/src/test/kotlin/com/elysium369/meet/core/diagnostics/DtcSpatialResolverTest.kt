package com.elysium369.meet.core.diagnostics

import com.elysium369.meet.data.visualdiagnostics.VisualDiagnosticSeedData
import com.elysium369.meet.domain.visualdiagnostics.EngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcSpatialResolverTest {
    @Test
    fun routesDtcFamiliesToTheirVehicleSystems() {
        assertEquals(
            DiagnosticSpatialSystem.BRAKES_STEERING,
            DtcSpatialResolver.resolve("C0035", "ABS").primarySystem,
        )
        assertEquals(
            DiagnosticSpatialSystem.BODY_ELECTRICAL,
            DtcSpatialResolver.resolve("B1318", "BCM").primarySystem,
        )
        assertEquals(
            DiagnosticSpatialSystem.COMMUNICATION_NETWORK,
            DtcSpatialResolver.resolve("U0100").primarySystem,
        )
        assertEquals(
            DiagnosticSpatialSystem.TRANSMISSION,
            DtcSpatialResolver.resolve("P0715", "TCM").primarySystem,
        )
        assertEquals(
            DiagnosticSpatialSystem.POWERTRAIN_ENGINE,
            DtcSpatialResolver.resolve("P0303", "ECM").primarySystem,
        )
    }

    @Test
    fun unknownEngineNeverSilentlyLoadsInlineFourSpecificParts() {
        val ids = VisualDiagnosticSeedData.components(EngineType.UNKNOWN).map { it.id }.toSet()

        assertTrue("ecu_pcm" in ids)
        assertFalse("alternator" in ids)
    }
}
