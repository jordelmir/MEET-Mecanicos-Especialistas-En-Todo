package com.elysium369.meet.visual3d.domain

import android.content.Context
import com.elysium369.meet.core.catalog.ProprietaryCanonical3dResolution
import com.elysium369.meet.core.catalog.ProprietaryCanonical3dResolver
import com.elysium369.meet.core.catalog.ProprietaryCatalogEntity
import com.elysium369.meet.core.catalog.VehicleTechnicalAtlasDescriptors
import com.elysium369.meet.core.catalog.physicalComponentName
import com.elysium369.meet.core.engine3d.UniversalCatalogSceneNode

sealed interface ProprietaryInline3dExperience {
    val authorityLabel: String
    val limitations: String

    data class Canonical(
        val resolution: ProprietaryCanonical3dResolution,
        val manifest: G4edAtlas3dManifest,
        val binding: G4edAtlas3dBinding,
    ) : ProprietaryInline3dExperience {
        override val authorityLabel = "RECONSTRUCCIÓN TÉCNICA DEDICADA"
        override val limitations =
            "Forma y proporciones ilustrativas; confirmar VIN, OEM, foto, conector y medidas."
    }

    data class Semantic(
        val node: UniversalCatalogSceneNode,
        val normalizedName: String,
    ) : ProprietaryInline3dExperience {
        override val authorityLabel = "REFERENCIA PROCEDIMENTAL POR ARQUETIPO"
        override val limitations =
            "Representación reconocible de forma general; no es CAD OEM ni autoridad dimensional."
    }
}

class ProprietaryInline3dRepository(context: Context) {
    private val resolver = ProprietaryCanonical3dResolver(context)
    private val g4edRepository = G4edAtlas3dRepository(context)
    private val technicalRepository = VehicleTechnicalAtlas3dRepository(context)

    fun load(entity: ProprietaryCatalogEntity): ProprietaryInline3dExperience? {
        if (entity.recordRole != "COMPONENT") return null
        val resolution = resolver.resolve(entity)
        if (resolution == null) {
            return semanticInline3dExperience(entity)
        }
        return runCatching {
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
            } ?: error("Canonical 3D binding is absent")
            ProprietaryInline3dExperience.Canonical(resolution, manifest, binding)
        }.getOrElse { semanticInline3dExperience(entity) }
    }
}

internal fun semanticInline3dExperience(
    entity: ProprietaryCatalogEntity,
): ProprietaryInline3dExperience.Semantic {
    require(entity.recordRole == "COMPONENT")
    val normalizedName = physicalComponentName(entity.nameOriginal)
    return ProprietaryInline3dExperience.Semantic(
        node = UniversalCatalogSceneNode(
            id = entity.id,
            name = normalizedName,
            systemId = entity.systemId,
            seed = entity.threeDimensionalBinding.seed,
            sectionId = entity.sectionId,
        ),
        normalizedName = normalizedName,
    )
}
