package com.elysium369.meet.visual3d

import com.elysium369.meet.visual3d.domain.ApplicabilityState
import com.elysium369.meet.visual3d.domain.NodeKind
import com.elysium369.meet.visual3d.domain.ServiceLevel
import com.elysium369.meet.visual3d.domain.VehicleTwinContract
import com.elysium369.meet.visual3d.domain.VehicleTwinNode
import com.elysium369.meet.visual3d.domain.VehicleTwinSystemAtlas
import com.elysium369.meet.visual3d.domain.VehicleTwinValidator
import com.elysium369.meet.visual3d.domain.VisualAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleTwinContractTest {
    @Test
    fun `atlas exposes all source systems at stable service levels`() {
        assertEquals(26, VehicleTwinSystemAtlas.systems.size)
        assertEquals(26, VehicleTwinSystemAtlas.systems.map { it.id }.distinct().size)
        assertEquals(ServiceLevel.COMPLETE_VEHICLE, ServiceLevel.entries.first())
        assertEquals(ServiceLevel.HARDWARE_CONSUMABLE, ServiceLevel.entries.last())
        assertTrue(VehicleTwinSystemAtlas.systems.all { it.nodeId == "system:${it.id}" })

        val referenceOnly = VehicleTwinSystemAtlas.systems
            .filter { it.applicability == ApplicabilityState.REFERENCE_ONLY }
            .map { it.id }
            .toSet()
        assertTrue(referenceOnly.containsAll(setOf("forced_induction", "adas", "hybrid_ev")))
        assertEquals(
            ApplicabilityState.INFORMATIONAL,
            VehicleTwinSystemAtlas.systems.single { it.id == "overview" }.applicability
        )
    }

    @Test
    fun `validator rejects duplicate orphan cyclic and overstated nodes`() {
        val invalid = VehicleTwinContract(
            nodes = listOf(
                VehicleTwinNode(
                    id = "vehicle:reference",
                    label = "Vehiculo",
                    level = ServiceLevel.COMPLETE_VEHICLE,
                    kind = NodeKind.VEHICLE,
                    parentNodeId = null,
                    systemId = null,
                    visualAuthority = VisualAuthority.GENERIC_SCHEMATIC,
                    applicability = ApplicabilityState.PROFILE_CONTEXT
                ),
                VehicleTwinNode(
                    id = "duplicate",
                    label = "A",
                    level = ServiceLevel.COMPONENT,
                    kind = NodeKind.PHYSICAL_COMPONENT,
                    parentNodeId = "missing",
                    systemId = "engine",
                    visualAuthority = VisualAuthority.VIN_OEM_VALIDATED,
                    applicability = ApplicabilityState.PROFILE_CONTEXT
                ),
                VehicleTwinNode(
                    id = "duplicate",
                    label = "B",
                    level = ServiceLevel.COMPONENT,
                    kind = NodeKind.PHYSICAL_COMPONENT,
                    parentNodeId = "duplicate",
                    systemId = "engine",
                    visualAuthority = VisualAuthority.GENERIC_SCHEMATIC,
                    applicability = ApplicabilityState.PROFILE_CONTEXT
                )
            ),
            bindings = emptyList()
        )

        val codes = VehicleTwinValidator.validate(invalid).map { it.code }.toSet()
        assertTrue("DUPLICATE_NODE_ID" in codes)
        assertTrue("MISSING_PARENT" in codes)
        assertTrue("PARENT_CYCLE" in codes)
        assertTrue("OVERSTATED_AUTHORITY" in codes)
    }
}
