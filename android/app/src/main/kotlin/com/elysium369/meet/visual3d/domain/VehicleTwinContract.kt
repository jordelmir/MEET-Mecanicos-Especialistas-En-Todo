package com.elysium369.meet.visual3d.domain

enum class ServiceLevel {
    COMPLETE_VEHICLE,
    SYSTEM,
    ASSEMBLY,
    COMPONENT,
    SERVICE_SUBPART,
    HARDWARE_CONSUMABLE
}

enum class NodeKind {
    VEHICLE,
    SYSTEM_ANCHOR,
    ASSEMBLY,
    PHYSICAL_COMPONENT,
    INFORMATIONAL_REFERENCE
}

enum class VisualAuthority {
    GENERIC_SCHEMATIC,
    SYSTEM_PROBABLE,
    VEHICLE_PROFILED,
    VIN_OEM_VALIDATED,
    VISUAL_CONFIRMED
}

enum class ApplicabilityState {
    PROFILE_CONTEXT,
    REFERENCE_ONLY,
    INFORMATIONAL
}

enum class CameraPreset {
    EXTERIOR_FRONT_3Q,
    EXTERIOR_REAR_3Q,
    ENGINE_BAY,
    UNDERBODY_FRONT,
    UNDERBODY_REAR,
    CABIN_FRONT,
    TRUNK,
    SELECTED_COMPONENT,
    REPAIR_STEP
}

enum class MaterialClass {
    STRUCTURE,
    POWERTRAIN,
    CHASSIS,
    ELECTRICAL,
    BODY,
    CABIN,
    FLUID,
    HARDWARE,
    INFORMATIONAL
}

data class TwinVector3(val x: Float, val y: Float, val z: Float)

data class VehicleTwinSystem(
    val id: String,
    val title: String,
    val nodeId: String = "system:$id",
    val anchor: TwinVector3,
    val cameraPreset: CameraPreset,
    val materialClass: MaterialClass,
    val applicability: ApplicabilityState
)

data class VehicleTwinNode(
    val id: String,
    val label: String,
    val level: ServiceLevel,
    val kind: NodeKind,
    val parentNodeId: String?,
    val systemId: String?,
    val visualAuthority: VisualAuthority,
    val applicability: ApplicabilityState,
    val materialClass: MaterialClass = MaterialClass.INFORMATIONAL,
    val locationAnchorId: String? = null,
    val isDimensionalModel: Boolean = false
)

data class VehicleTwinBinding(
    val entityId: String,
    val nodeId: String,
    val systemId: String,
    val assemblyId: String,
    val locationAnchorId: String,
    val sourceDocumentId: String,
    val sourceFileName: String,
    val sourceBlockId: String,
    val sourceTextHash: String,
    val sourceOrder: Int,
    val sourceVisualAuthority: String,
    val isDimensionalModel: Boolean
)

data class VehicleTwinContract(
    val nodes: List<VehicleTwinNode>,
    val bindings: List<VehicleTwinBinding>
) {
    val renderedOverviewNodeCount: Int
        get() = 1 + VehicleTwinSystemAtlas.systems.count {
            it.applicability != ApplicabilityState.INFORMATIONAL
        }
}

data class VehicleTwinValidationError(
    val code: String,
    val subjectId: String,
    val message: String
)

object VehicleTwinSystemAtlas {
    private fun system(
        id: String,
        title: String,
        x: Float,
        y: Float,
        z: Float,
        camera: CameraPreset,
        material: MaterialClass,
        applicability: ApplicabilityState = ApplicabilityState.PROFILE_CONTEXT
    ) = VehicleTwinSystem(
        id = id,
        title = title,
        anchor = TwinVector3(x, y, z),
        cameraPreset = camera,
        materialClass = material,
        applicability = applicability
    )

    val systems: List<VehicleTwinSystem> = listOf(
        system("structure", "Nucleo y estructura", 0.0f, 0.2f, 0.0f, CameraPreset.EXTERIOR_FRONT_3Q, MaterialClass.STRUCTURE),
        system("engine", "Motor de combustion", 0.0f, 0.55f, 1.15f, CameraPreset.ENGINE_BAY, MaterialClass.POWERTRAIN),
        system("intake", "Admision de aire", -0.55f, 0.72f, 1.2f, CameraPreset.ENGINE_BAY, MaterialClass.POWERTRAIN),
        system("forced_induction", "Sobrealimentacion", 0.55f, 0.7f, 1.15f, CameraPreset.ENGINE_BAY, MaterialClass.POWERTRAIN, ApplicabilityState.REFERENCE_ONLY),
        system("transmission", "Transmision y tren motriz", 0.0f, 0.15f, 0.25f, CameraPreset.UNDERBODY_FRONT, MaterialClass.POWERTRAIN),
        system("suspension", "Suspension", -0.85f, 0.05f, 0.2f, CameraPreset.UNDERBODY_FRONT, MaterialClass.CHASSIS),
        system("steering", "Direccion", -0.45f, 0.38f, 0.7f, CameraPreset.UNDERBODY_FRONT, MaterialClass.CHASSIS),
        system("brakes", "Frenos", 0.86f, 0.03f, 0.12f, CameraPreset.UNDERBODY_FRONT, MaterialClass.CHASSIS),
        system("wheels", "Ruedas y neumaticos", 0.92f, -0.08f, -0.35f, CameraPreset.EXTERIOR_FRONT_3Q, MaterialClass.CHASSIS),
        system("electrical", "Sistema electrico", 0.4f, 0.68f, 1.0f, CameraPreset.ENGINE_BAY, MaterialClass.ELECTRICAL),
        system("control_modules", "ECUs y controladores", 0.15f, 0.68f, 0.35f, CameraPreset.CABIN_FRONT, MaterialClass.ELECTRICAL),
        system("sensors", "Sensores", -0.2f, 0.7f, 0.8f, CameraPreset.ENGINE_BAY, MaterialClass.ELECTRICAL),
        system("actuators", "Actuadores", 0.2f, 0.5f, 0.75f, CameraPreset.ENGINE_BAY, MaterialClass.ELECTRICAL),
        system("lighting", "Iluminacion", 0.0f, 0.5f, 1.75f, CameraPreset.EXTERIOR_FRONT_3Q, MaterialClass.ELECTRICAL),
        system("hvac", "HVAC y climatizacion", 0.0f, 0.75f, 0.1f, CameraPreset.CABIN_FRONT, MaterialClass.CABIN),
        system("passive_safety", "Seguridad pasiva", -0.25f, 0.9f, -0.2f, CameraPreset.CABIN_FRONT, MaterialClass.CABIN),
        system("adas", "ADAS y asistencia", 0.0f, 0.65f, 1.9f, CameraPreset.EXTERIOR_FRONT_3Q, MaterialClass.ELECTRICAL, ApplicabilityState.REFERENCE_ONLY),
        system("body", "Carroceria exterior", 0.0f, 0.9f, -0.8f, CameraPreset.EXTERIOR_REAR_3Q, MaterialClass.BODY),
        system("wipers", "Limpiaparabrisas y lavado", 0.0f, 1.05f, 0.65f, CameraPreset.EXTERIOR_FRONT_3Q, MaterialClass.BODY),
        system("interior", "Interior", 0.0f, 0.82f, -0.3f, CameraPreset.CABIN_FRONT, MaterialClass.CABIN),
        system("infotainment", "Infotainment y comunicacion", 0.0f, 0.88f, 0.0f, CameraPreset.CABIN_FRONT, MaterialClass.CABIN),
        system("access", "Cierre, acceso e inmovilizador", 0.75f, 0.72f, -0.3f, CameraPreset.EXTERIOR_FRONT_3Q, MaterialClass.BODY),
        system("hybrid_ev", "Hibridos y electricos", 0.0f, 0.1f, -0.15f, CameraPreset.UNDERBODY_REAR, MaterialClass.ELECTRICAL, ApplicabilityState.REFERENCE_ONLY),
        system("fluids", "Fluidos, consumibles y desgaste", -0.2f, 0.25f, 0.65f, CameraPreset.ENGINE_BAY, MaterialClass.FLUID),
        system("hardware", "Fasteners, sellos y hardware", 0.45f, 0.25f, 0.45f, CameraPreset.SELECTED_COMPONENT, MaterialClass.HARDWARE),
        system("overview", "Indice funcional y reglas", 0.0f, 0.0f, 0.0f, CameraPreset.EXTERIOR_FRONT_3Q, MaterialClass.INFORMATIONAL, ApplicabilityState.INFORMATIONAL)
    )

    private val byId = systems.associateBy(VehicleTwinSystem::id)

    fun require(systemId: String): VehicleTwinSystem =
        requireNotNull(byId[systemId]) { "Unknown proprietary system: $systemId" }
}

object VehicleTwinValidator {
    fun validate(contract: VehicleTwinContract): List<VehicleTwinValidationError> = buildList {
        contract.nodes.groupBy(VehicleTwinNode::id).filterValues { it.size > 1 }.forEach { (id, _) ->
            add(error("DUPLICATE_NODE_ID", id, "Node ID must be unique"))
        }

        val nodesById = contract.nodes.associateBy(VehicleTwinNode::id)
        contract.nodes.forEach { node ->
            if (node.parentNodeId != null && node.parentNodeId !in nodesById) {
                add(error("MISSING_PARENT", node.id, "Parent ${node.parentNodeId} does not exist"))
            }
            if (node.visualAuthority == VisualAuthority.VIN_OEM_VALIDATED ||
                node.visualAuthority == VisualAuthority.VISUAL_CONFIRMED
            ) {
                add(error("OVERSTATED_AUTHORITY", node.id, "Evidence-backed authority was not supplied"))
            }
            if (node.kind == NodeKind.INFORMATIONAL_REFERENCE && node.isDimensionalModel) {
                add(error("INFORMATIONAL_GEOMETRY", node.id, "Informational nodes cannot be dimensional"))
            }
        }

        contract.nodes.forEach { start ->
            val visited = mutableSetOf<String>()
            var current: VehicleTwinNode? = start
            while (current != null && current.parentNodeId != null) {
                if (!visited.add(current.id)) {
                    add(error("PARENT_CYCLE", start.id, "Parent graph contains a cycle"))
                    break
                }
                current = nodesById[current.parentNodeId]
            }
        }

        contract.bindings.groupBy(VehicleTwinBinding::entityId).filterValues { it.size > 1 }.forEach { (id, _) ->
            add(error("DUPLICATE_ENTITY_BINDING", id, "Entity has more than one primary binding"))
        }
        contract.bindings.forEach { binding ->
            val node = nodesById[binding.nodeId]
            if (node == null) add(error("MISSING_BOUND_NODE", binding.entityId, "Bound node does not exist"))
            if (binding.sourceTextHash.isBlank()) add(error("MISSING_SOURCE_HASH", binding.entityId, "Source hash is required"))
            if (binding.isDimensionalModel) add(error("OVERSTATED_DIMENSION", binding.entityId, "Generic mapping cannot be dimensional"))
        }
    }.distinct()

    private fun error(code: String, subjectId: String, message: String) =
        VehicleTwinValidationError(code, subjectId, message)
}
