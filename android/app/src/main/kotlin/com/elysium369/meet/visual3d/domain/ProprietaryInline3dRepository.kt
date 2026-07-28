package com.elysium369.meet.visual3d.domain

import android.content.Context
import com.elysium369.meet.core.catalog.ProprietaryCanonical3dResolution
import com.elysium369.meet.core.catalog.ProprietaryCanonical3dResolver
import com.elysium369.meet.core.catalog.ProprietaryCatalogEntity
import com.elysium369.meet.core.catalog.VehicleTechnicalAtlasDescriptors

data class ProprietaryInline3dExperience(
    val resolution: ProprietaryCanonical3dResolution,
    val manifest: G4edAtlas3dManifest,
    val binding: G4edAtlas3dBinding,
)

class ProprietaryInline3dRepository(context: Context) {
    private val resolver = ProprietaryCanonical3dResolver(context)
    private val g4edRepository = G4edAtlas3dRepository(context)
    private val technicalRepository = VehicleTechnicalAtlas3dRepository(context)

    fun load(entity: ProprietaryCatalogEntity): ProprietaryInline3dExperience? {
        val resolution = resolver.resolve(entity) ?: return null
        val element = resolution.part.element
        val manifest = if (element.canonicalId.startsWith("g4ed-")) {
            g4edRepository.manifest(element.visual.packId)
        } else {
            val domainId = requireNotNull(
                VehicleTechnicalAtlasDescriptors.forCanonicalId(element.canonicalId),
            ).domainId
            technicalRepository.manifest(domainId, element.visual.packId)
        }
        val binding = if (element.canonicalId.startsWith("g4ed-")) {
            G4edAtlas3dCatalog.bindingFor(element, manifest)
        } else {
            VehicleTechnicalAtlas3dCatalog.bindingFor(element, manifest)
        } ?: return null
        return ProprietaryInline3dExperience(resolution, manifest, binding)
    }
}
