package com.elysium369.meet.visual3d

import com.elysium369.meet.core.catalog.PROPRIETARY_CATALOG_ENTITY_INDEX_ASSET
import com.elysium369.meet.core.catalog.PROPRIETARY_CATALOG_MANIFEST_ASSET
import com.elysium369.meet.core.catalog.ProprietaryCatalogParser
import com.elysium369.meet.visual3d.data.ProprietaryVehicleTwinMapper
import com.elysium369.meet.visual3d.domain.ApplicabilityState
import com.elysium369.meet.visual3d.domain.NodeKind
import com.elysium369.meet.visual3d.domain.VehicleTwinValidator
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProprietaryVehicleTwinMapperTest {
    private fun asset(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
        File("android/app/src/main/assets/$path")
    ).firstOrNull(File::isFile) ?: error("Missing proprietary asset: $path")

    private val manifest = ProprietaryCatalogParser.decodeManifest(
        asset(PROPRIETARY_CATALOG_MANIFEST_ASSET).readText()
    )
    private val index = ProprietaryCatalogParser.decodeEntityIndex(
        asset(PROPRIETARY_CATALOG_ENTITY_INDEX_ASSET).readText()
    )

    @Test
    fun `all 4753 proprietary components receive one honest primary binding`() {
        val components = index.entities.filter { it.recordRole == "COMPONENT" }
        val contract = ProprietaryVehicleTwinMapper.map(manifest, index)

        assertEquals(4_753, components.size)
        assertEquals(components.size, contract.bindings.size)
        assertEquals(components.size, contract.bindings.map { it.entityId }.distinct().size)
        assertEquals(components.map { it.id }.toSet(), contract.bindings.map { it.entityId }.toSet())
        assertTrue(VehicleTwinValidator.validate(contract).isEmpty())

        contract.bindings.forEach { binding ->
            val source = components.single { it.id == binding.entityId }
            assertEquals(source.sourceDocumentId, binding.sourceDocumentId)
            assertEquals(source.sourceBlockId, binding.sourceBlockId)
            assertEquals(source.sourceTextHash, binding.sourceTextHash)
            assertEquals(source.sourceOrder, binding.sourceOrder)
            assertFalse(binding.isDimensionalModel)
        }
    }

    @Test
    fun `reference and informational systems never claim installed geometry`() {
        val contract = ProprietaryVehicleTwinMapper.map(manifest, index)
        val nodesById = contract.nodes.associateBy { it.id }

        contract.bindings.filter { it.systemId in setOf("forced_induction", "adas", "hybrid_ev") }
            .forEach { binding ->
                assertEquals(ApplicabilityState.REFERENCE_ONLY, nodesById.getValue(binding.nodeId).applicability)
            }

        contract.bindings.filter { it.systemId == "overview" }.forEach { binding ->
            val node = nodesById.getValue(binding.nodeId)
            assertEquals(ApplicabilityState.INFORMATIONAL, node.applicability)
            assertEquals(NodeKind.INFORMATIONAL_REFERENCE, node.kind)
        }
    }
}
