package com.elysium369.meet.visual3d.domain

import com.elysium369.meet.core.catalog.VehicleTechnicalAtlasDescriptor
import com.elysium369.meet.core.catalog.VehicleTechnicalAtlasDescriptors
import com.elysium369.meet.core.catalog.VehicleTechnicalAtlasParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VehicleTechnicalAtlas3dContractTest {
    private fun asset(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
        File("android/app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: error("Missing asset $path")

    @Test
    fun `110 signed manifests bind every one of the 5985 technical elements`() {
        var packs = 0
        var bindings = 0
        VehicleTechnicalAtlasDescriptors.all.forEach { descriptor ->
            val atlas = VehicleTechnicalAtlasParser.decode(asset(descriptor.assetPath).readText(), descriptor)
            atlas.elements.groupBy { it.visual.packId }.forEach { (packId, elements) ->
                val manifest = manifest(descriptor, packId)
                assertEquals(elements.size, manifest.bindings.size)
                elements.forEach { element ->
                    val binding = requireNotNull(
                        VehicleTechnicalAtlas3dCatalog.bindingFor(element, manifest),
                    )
                    assertFalse(binding.oemClaim)
                    assertFalse(binding.dimensional)
                }
                packs += 1
                bindings += manifest.bindings.size
            }
        }
        assertEquals(110, packs)
        assertEquals(5_985, bindings)
    }

    private fun manifest(
        descriptor: VehicleTechnicalAtlasDescriptor,
        packId: String,
    ): G4edAtlas3dManifest =
        VehicleTechnicalAtlas3dManifestParser.decode(
            asset(VehicleTechnicalAtlas3dCatalog.manifestAssetPath(descriptor.domainId, packId))
                .readText(),
            descriptor,
        )
}
