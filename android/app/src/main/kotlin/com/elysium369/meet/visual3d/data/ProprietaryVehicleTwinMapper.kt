package com.elysium369.meet.visual3d.data

import com.elysium369.meet.core.catalog.ProprietaryCatalogManifest
import com.elysium369.meet.core.catalog.ProprietaryEntityIndex
import com.elysium369.meet.visual3d.domain.ApplicabilityState
import com.elysium369.meet.visual3d.domain.MaterialClass
import com.elysium369.meet.visual3d.domain.NodeKind
import com.elysium369.meet.visual3d.domain.ServiceLevel
import com.elysium369.meet.visual3d.domain.VehicleTwinBinding
import com.elysium369.meet.visual3d.domain.VehicleTwinContract
import com.elysium369.meet.visual3d.domain.VehicleTwinNode
import com.elysium369.meet.visual3d.domain.VehicleTwinSystemAtlas
import com.elysium369.meet.visual3d.domain.VisualAuthority

object ProprietaryVehicleTwinMapper {
    private const val VEHICLE_NODE_ID = "vehicle:reference"

    fun map(
        manifest: ProprietaryCatalogManifest,
        index: ProprietaryEntityIndex
    ): VehicleTwinContract {
        require(manifest.systems.map { it.id }.toSet() == VehicleTwinSystemAtlas.systems.map { it.id }.toSet()) {
            "Vehicle-twin atlas must match every proprietary system"
        }

        val vehicleNode = VehicleTwinNode(
            id = VEHICLE_NODE_ID,
            label = "Vehiculo 3D de referencia",
            level = ServiceLevel.COMPLETE_VEHICLE,
            kind = NodeKind.VEHICLE,
            parentNodeId = null,
            systemId = null,
            visualAuthority = VisualAuthority.GENERIC_SCHEMATIC,
            applicability = ApplicabilityState.PROFILE_CONTEXT,
            materialClass = MaterialClass.BODY
        )

        val systemNodes = VehicleTwinSystemAtlas.systems.map { system ->
            VehicleTwinNode(
                id = system.nodeId,
                label = manifest.systems.single { it.id == system.id }.title,
                level = ServiceLevel.SYSTEM,
                kind = if (system.applicability == ApplicabilityState.INFORMATIONAL) {
                    NodeKind.INFORMATIONAL_REFERENCE
                } else {
                    NodeKind.SYSTEM_ANCHOR
                },
                parentNodeId = VEHICLE_NODE_ID,
                systemId = system.id,
                visualAuthority = VisualAuthority.GENERIC_SCHEMATIC,
                applicability = system.applicability,
                materialClass = system.materialClass,
                locationAnchorId = system.nodeId
            )
        }

        val assemblyNodes = manifest.sections.map { section ->
            val system = VehicleTwinSystemAtlas.require(section.systemId)
            VehicleTwinNode(
                id = assemblyNodeId(section.id),
                label = section.titleOriginal,
                level = ServiceLevel.ASSEMBLY,
                kind = if (system.applicability == ApplicabilityState.INFORMATIONAL) {
                    NodeKind.INFORMATIONAL_REFERENCE
                } else {
                    NodeKind.ASSEMBLY
                },
                parentNodeId = system.nodeId,
                systemId = system.id,
                visualAuthority = VisualAuthority.GENERIC_SCHEMATIC,
                applicability = system.applicability,
                materialClass = system.materialClass,
                locationAnchorId = system.nodeId
            )
        }

        val components = index.entities.filter { it.recordRole == "COMPONENT" }
        val componentNodes = components.map { entity ->
            val system = VehicleTwinSystemAtlas.require(entity.systemId)
            VehicleTwinNode(
                id = componentNodeId(entity.id),
                label = entity.nameOriginal,
                level = ServiceLevel.COMPONENT,
                kind = if (system.applicability == ApplicabilityState.INFORMATIONAL) {
                    NodeKind.INFORMATIONAL_REFERENCE
                } else {
                    NodeKind.PHYSICAL_COMPONENT
                },
                parentNodeId = assemblyNodeId(entity.sectionId),
                systemId = entity.systemId,
                visualAuthority = VisualAuthority.GENERIC_SCHEMATIC,
                applicability = system.applicability,
                materialClass = system.materialClass,
                locationAnchorId = system.nodeId,
                isDimensionalModel = false
            )
        }

        val bindings = components.map { entity ->
            VehicleTwinBinding(
                entityId = entity.id,
                nodeId = componentNodeId(entity.id),
                systemId = entity.systemId,
                assemblyId = assemblyNodeId(entity.sectionId),
                locationAnchorId = VehicleTwinSystemAtlas.require(entity.systemId).nodeId,
                sourceDocumentId = entity.sourceDocumentId,
                sourceFileName = entity.sourceFileName,
                sourceBlockId = entity.sourceBlockId,
                sourceTextHash = entity.sourceTextHash,
                sourceOrder = entity.sourceOrder,
                sourceVisualAuthority = entity.threeDimensionalBinding.visualAuthority,
                isDimensionalModel = entity.threeDimensionalBinding.isDimensionalModel
            )
        }

        return VehicleTwinContract(
            nodes = listOf(vehicleNode) + systemNodes + assemblyNodes + componentNodes,
            bindings = bindings
        )
    }

    fun componentNodeId(entityId: String): String = "component:$entityId"

    fun assemblyNodeId(sectionId: String): String = "assembly:$sectionId"
}
