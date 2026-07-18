package com.elysium369.meet.visual3d

import com.elysium369.meet.core.engine3d.UniversalCatalogSceneNode
import com.elysium369.meet.visual3d.domain.CatalogSemanticScenePlanner
import com.elysium369.meet.visual3d.domain.GenericVehicleSystemsAssetContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GenericVehicleSystemsAssetContractTest {
    @Test
    fun `every requested system resolves to one staged asset`() {
        val requested = setOf(
            "intake", "forced_induction", "transmission", "suspension", "steering",
            "brakes", "wheels", "electrical", "control_modules", "sensors", "actuators"
        )

        assertEquals(
            requested,
            requested.filterTo(linkedSetOf()) {
                GenericVehicleSystemsAssetContract.assetForSystem(it) != null
            }
        )
        assertNull(GenericVehicleSystemsAssetContract.assetForSystem("engine"))
    }

    @Test
    fun `literal source matching is scoped by proprietary system`() {
        val asset = GenericVehicleSystemsAssetContract.electricalControl
        val valid = UniversalCatalogSceneNode("ecm", "ECU / ECM motor", "control_modules", 1L)
        val wrongSystem = UniversalCatalogSceneNode("ecm-wrong", "ECU / ECM motor", "electrical", 2L)

        assertEquals("ecm", GenericVehicleSystemsAssetContract.bindingForSourceNode(asset, valid)?.meshKey)
        assertNull(GenericVehicleSystemsAssetContract.bindingForSourceNode(asset, wrongSystem))
    }

    @Test
    fun `combined asset keeps one primary record per mechanical family`() {
        val asset = GenericVehicleSystemsAssetContract.steeringBrakesWheels
        val nodes = listOf(
            UniversalCatalogSceneNode("disc-first", "Discos delanteros", "brakes", 1L),
            UniversalCatalogSceneNode("disc-proof", "Discos delanteros", "brakes", 2L),
            UniversalCatalogSceneNode("rack", "Cremallera de dirección", "steering", 3L),
            UniversalCatalogSceneNode("unsupported", "Tabla de torque", "brakes", 4L)
        )

        assertEquals(
            listOf("disc-first", "rack"),
            GenericVehicleSystemsAssetContract.sourceBackedNodes(asset, nodes).map { it.id }
        )
    }

    @Test
    fun `context meshes never manufacture a proprietary selection`() {
        val asset = GenericVehicleSystemsAssetContract.transmissionDrivetrain
        val placements = CatalogSemanticScenePlanner.placements(
            listOf(UniversalCatalogSceneNode("converter", "Convertidor de par", "transmission", 1L)),
            null
        )

        assertNull(
            GenericVehicleSystemsAssetContract.placementForNodeName(
                asset,
                "system_mesh__planetary_gearset__sun_gear",
                placements
            )
        )
    }

    @Test
    fun `mesh selection resolves the exact source entity`() {
        val asset = GenericVehicleSystemsAssetContract.intakeBoost
        val placements = CatalogSemanticScenePlanner.placements(
            listOf(UniversalCatalogSceneNode("throttle", "8. Mariposa de aceleración", "intake", 1L)),
            null
        )

        val placement = GenericVehicleSystemsAssetContract.placementForNodeName(
            asset,
            "system_mesh__throttle_body__bore",
            placements
        )

        assertEquals("throttle", placement?.node?.id)
        assertTrue(
            GenericVehicleSystemsAssetContract.isNodeSelected(
                asset,
                "system_mesh__throttle_body__plate",
                placements,
                "throttle"
            )
        )
        assertFalse(
            GenericVehicleSystemsAssetContract.isNodeSelected(
                asset,
                "system_mesh__throttle_body__plate",
                placements,
                null
            )
        )
    }

    @Test
    fun `all service explosions remain bounded and return to origin`() {
        GenericVehicleSystemsAssetContract.assets.forEach { asset ->
            asset.bindings.forEach { binding ->
                val nodeName = "${GenericVehicleSystemsAssetContract.MESH_NODE_PREFIX}${binding.meshKey}__audit"
                val assembled = GenericVehicleSystemsAssetContract.serviceOffset(asset, nodeName, 0f)
                val exploded = GenericVehicleSystemsAssetContract.serviceOffset(asset, nodeName, 1f)

                assertEquals(0f, assembled.x, 0f)
                assertEquals(0f, assembled.y, 0f)
                assertEquals(0f, assembled.z, 0f)
                assertTrue(abs(exploded.x) <= 0.82f)
                assertTrue(abs(exploded.y) <= 0.78f)
                assertTrue(abs(exploded.z) <= 0.30f)
            }
        }
    }

    @Test
    fun `asset ids paths mesh keys and scoped aliases are unique`() {
        val assets = GenericVehicleSystemsAssetContract.assets

        assertEquals(assets.size, assets.map { it.id }.distinct().size)
        assertEquals(assets.size, assets.map { it.assetPath }.distinct().size)
        assets.forEach { asset ->
            assertTrue(asset.requiredMeshKeys.isNotEmpty())
            assertEquals(asset.bindings.size, asset.bindings.map { it.meshKey }.distinct().size)
            val aliases = asset.bindings.flatMap { it.sourceAliases }
            assertEquals(
                aliases.size,
                aliases.map { "${it.systemId}:${it.literalName.lowercase()}" }.distinct().size
            )
        }
    }
}
