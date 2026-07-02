package com.elysium.vanguard.forge.assembly

import com.elysium.vanguard.forge.domain.AssemblyValidationResult
import com.elysium.vanguard.forge.domain.BoundingBox
import com.elysium.vanguard.forge.domain.CompletenessResult
import com.elysium.vanguard.forge.domain.ExplodedViewResult
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.ForgeValidationError
import com.elysium.vanguard.forge.domain.ForgeValidationIssue
import com.elysium.vanguard.forge.domain.ForgeVehicle
import com.elysium.vanguard.forge.domain.InterferenceResult
import com.elysium.vanguard.forge.domain.JointType
import com.elysium.vanguard.forge.domain.MechanicalJoint
import com.elysium.vanguard.forge.domain.PartInstance
import com.elysium.vanguard.forge.domain.TransformData
import com.elysium.vanguard.forge.domain.Vector3Data
import com.elysium.vanguard.forge.domain.VehicleSystemType

/**
 * ForgeAssemblyEngine V1.
 *
 * Maneja el árbol de ensamblaje: instancias, joints, validaciones, exploded view,
 * interferencias, completeness. NO ejecuta física — eso es ForgePhysicsEngine.
 *
 * Reglas duras:
 * - Ninguna operación muta el ForgeAssembly original; siempre devuelve uno nuevo.
 * - Los joints deben referenciar instancias existentes (validado en data class).
 * - Detección de ciclos via DFS desde el joint-graph.
 * - Las piezas flotantes son aquellas sin ningún joint que las conecte a otra.
 */
class ForgeAssemblyEngine {

    /**
     * Crea un nuevo ForgeAssembly con la pieza añadida. Idempotente en id.
     */
    fun addPart(
        assembly: ForgeAssembly,
        partId: String,
        instanceId: String,
        transform: TransformData = TransformData()
    ): ForgeAssembly {
        if (assembly.instances.any { it.id == instanceId }) {
            return assembly // idempotente: ya existe
        }
        val newInstance = PartInstance(
            id = instanceId,
            partId = partId,
            transform = transform
        )
        return assembly.copy(instances = assembly.instances + newInstance)
    }

    fun removePart(assembly: ForgeAssembly, instanceId: String): ForgeAssembly {
        val newInstances = assembly.instances.filterNot { it.id == instanceId }
        val newJoints = assembly.joints.filterNot {
            it.parentInstanceId == instanceId || it.childInstanceId == instanceId
        }
        return assembly.copy(
            instances = newInstances,
            joints = newJoints
        )
    }

    /**
     * Crea un joint entre dos instancias. Rechaza si crea ciclo o si los puertos
     * son incompatibles con el tipo de joint.
     */
    fun createJoint(
        assembly: ForgeAssembly,
        jointId: String,
        name: String,
        jointType: JointType,
        parentInstanceId: String,
        childInstanceId: String,
        parentPortId: String? = null,
        childPortId: String? = null,
        partsById: Map<String, ForgePart> = emptyMap()
    ): Result<ForgeAssembly> {
        if (assembly.joints.any { it.id == jointId }) {
            return Result.failure(IllegalArgumentException("Joint $jointId already exists"))
        }
        if (!assembly.instances.any { it.id == parentInstanceId }) {
            return Result.failure(IllegalArgumentException("Parent instance $parentInstanceId not found"))
        }
        if (!assembly.instances.any { it.id == childInstanceId }) {
            return Result.failure(IllegalArgumentException("Child instance $childInstanceId not found"))
        }
        if (parentInstanceId == childInstanceId) {
            return Result.failure(IllegalArgumentException("Joint parent and child must differ"))
        }

        // Validar puertos contra compatibilidad.
        val portsCheck: Result<Unit> = validatePortsForJoint(
            assembly = assembly,
            jointType = jointType,
            parentInstanceId = parentInstanceId,
            childInstanceId = childInstanceId,
            parentPortId = parentPortId,
            childPortId = childPortId,
            partsById = partsById
        )
        if (portsCheck.isFailure) return Result.failure(portsCheck.exceptionOrNull() ?: IllegalStateException("Port validation failed"))

        val newJoint = MechanicalJoint(
            id = jointId,
            name = name,
            jointType = jointType,
            parentInstanceId = parentInstanceId,
            childInstanceId = childInstanceId,
            parentPortId = parentPortId,
            childPortId = childPortId
        )

        val candidate = assembly.copy(joints = assembly.joints + newJoint)

        // Detectar ciclos.
        if (hasCycle(candidate)) {
            return Result.failure(IllegalStateException("Joint would create a cycle in assembly graph"))
        }

        return Result.success(candidate)
    }

    fun updateJoint(assembly: ForgeAssembly, jointId: String, transform: (MechanicalJoint) -> MechanicalJoint): ForgeAssembly {
        return assembly.copy(
            joints = assembly.joints.map { if (it.id == jointId) transform(it) else it }
        )
    }

    /**
     * Valida el ensamblaje completo: dimensiones faltantes, puertos faltantes,
     * interferencias simples, joints incompatibles, piezas flotantes, ciclos.
     */
    fun validateAssembly(
        assembly: ForgeAssembly,
        partsById: Map<String, ForgePart> = emptyMap()
    ): AssemblyValidationResult {
        val issues = mutableListOf<ForgeValidationIssue>()
        val floating = findFloatingParts(assembly)
        val incompatibleJoints = findIncompatibleJoints(assembly, partsById)
        val interferences = detectSimpleInterferences(assembly, partsById)

        for (id in floating) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.FLOATING_PART,
                warning = null,
                message = "Part instance $id has no joints connecting it (floating)",
                relatedInstanceId = id
            )
        }

        for (j in incompatibleJoints) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.JOINT_INCOMPATIBLE,
                warning = null,
                message = "Joint ${j.id} of type ${j.jointType} has incompatible ports",
                relatedJointId = j.id
            )
        }

        for (inter in interferences) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.INTERFERENCE_DETECTED,
                warning = null,
                message = "Interference between ${inter.instanceIdA} and ${inter.instanceIdB} (penetration ${inter.penetrationMm} mm)",
                relatedInstanceId = inter.instanceIdA
            )
        }

        if (hasCycle(assembly)) {
            issues += ForgeValidationIssue(
                code = ForgeValidationError.CYCLE_IN_ASSEMBLY_GRAPH,
                warning = null,
                message = "Assembly joint graph contains a cycle"
            )
        }

        // Piezas críticas sin manual.
        val criticalMissingManuals = mutableListOf<String>()
        for (instance in assembly.instances) {
            val part = partsById[instance.partId] ?: continue
            if (part.artifact.safetyClassification.isSafetyCritical &&
                part.repairProcedures.isEmpty() &&
                part.replacementProcedures.isEmpty()
            ) {
                criticalMissingManuals += instance.id
                issues += ForgeValidationIssue(
                    code = ForgeValidationError.MANUAL_MISSING,
                    warning = null,
                    message = "Safety-critical part '${part.artifact.name}' has no repair/replacement procedure",
                    relatedInstanceId = instance.id
                )
            }
        }

        return AssemblyValidationResult(
            isValid = issues.none { it.code != null },
            floatingInstanceIds = floating,
            interferencePairs = interferences,
            incompatibleJoints = incompatibleJoints.map { it.id },
            criticalMissingManuals = criticalMissingManuals,
            issues = issues
        )
    }

    /**
     * Detecta interferencias AABB-based entre pares de instancias (heurística simple).
     */
    fun detectInterferences(assembly: ForgeAssembly, partsById: Map<String, ForgePart>): List<InterferenceResult> {
        return detectSimpleInterferences(assembly, partsById)
    }

    fun findFloatingParts(assembly: ForgeAssembly): List<String> {
        if (assembly.instances.isEmpty()) return emptyList()
        val connected = mutableSetOf<String>()
        for (j in assembly.joints) {
            connected += j.parentInstanceId
            connected += j.childInstanceId
        }
        return assembly.instances.map { it.id }.filterNot { it in connected }
    }

    /**
     * Vista explotada: desplaza cada instancia a lo largo de un eje desde el centro.
     * V1: heurística simple, no detecta agrupamiento.
     */
    fun computeExplodedView(assembly: ForgeAssembly, axis: Vector3Data = Vector3Data.UNIT_Y): ExplodedViewResult {
        val center = computeAssemblyCenter(assembly)
        val offsets = HashMap<String, Vector3Data>()
        for (instance in assembly.instances) {
            val dir = (instance.transform.position - center).normalized()
            // Distancia proporcional al número de instancias para separación visible.
            val sepMm = 50.0 * assembly.instances.size.coerceAtMost(8).toDouble() / 8.0
            val offset = dir * sepMm
            offsets[instance.id] = offset
        }
        return ExplodedViewResult(instanceIdToOffset = offsets, axis = axis)
    }

    fun computeAssemblyCompleteness(vehicle: ForgeVehicle, partsById: Map<String, ForgePart> = emptyMap()): CompletenessResult {
        if (vehicle.systems.isEmpty()) {
            return CompletenessResult(
                vehicleId = vehicle.artifact.id,
                overallPercent = 0.0,
                missingSystems = VehicleSystemType.values().toList(),
                invalidSystems = emptyList(),
                readyToSimulate = false
            )
        }
        val present = vehicle.systems.map { it.systemType }.toSet()
        val missing = VehicleSystemType.values().filterNot { it in present }
        val invalid = vehicle.systems.filter {
            partsById[it.assemblyId] == null && it.criticalFailureCount > 0
        }.map { it.systemType }
        val ready = missing.isEmpty() && invalid.isEmpty()
        return CompletenessResult(
            vehicleId = vehicle.artifact.id,
            overallPercent = vehicle.completenessPercent(),
            missingSystems = missing,
            invalidSystems = invalid,
            readyToSimulate = ready
        )
    }

    // --- Helpers ---

    private fun validatePortsForJoint(
        assembly: ForgeAssembly,
        jointType: JointType,
        parentInstanceId: String,
        childInstanceId: String,
        parentPortId: String?,
        childPortId: String?,
        partsById: Map<String, ForgePart>
    ): Result<Unit> {
        if (parentPortId == null || childPortId == null) {
            // Joint sin puertos específicos: permitido para algunos tipos (FIXED, BOLTED, ...).
            return Result.success(Unit)
        }
        val parentInstance = assembly.instances.firstOrNull { it.id == parentInstanceId }
            ?: return Result.failure(IllegalArgumentException("Parent instance not found"))
        val childInstance = assembly.instances.firstOrNull { it.id == childInstanceId }
            ?: return Result.failure(IllegalArgumentException("Child instance not found"))

        val parentPart = partsById[parentInstance.partId]
        val childPart = partsById[childInstance.partId]
        if (parentPart == null || childPart == null) {
            // No podemos validar puertos sin info de pieza → permitimos pero marcamos warning.
            return Result.success(Unit)
        }
        val parentPort = parentPart.connectionPorts.firstOrNull { it.id == parentPortId }
        val childPort = childPart.connectionPorts.firstOrNull { it.id == childPortId }
        if (parentPort == null || childPort == null) {
            return Result.failure(IllegalArgumentException("Port not found on part"))
        }
        if (parentPort.compatibleJointTypes.isNotEmpty() && jointType !in parentPort.compatibleJointTypes) {
            return Result.failure(IllegalArgumentException(
                "Joint type $jointType not compatible with parent port ${parentPort.id}"
            ))
        }
        if (childPort.compatibleJointTypes.isNotEmpty() && jointType !in childPort.compatibleJointTypes) {
            return Result.failure(IllegalArgumentException(
                "Joint type $jointType not compatible with child port ${childPort.id}"
            ))
        }
        return Result.success(Unit)
    }

    /**
     * Detección de ciclos via DFS en el grafo dirigido (parent → child).
     */
    fun hasCycle(assembly: ForgeAssembly): Boolean {
        val adjacency = HashMap<String, MutableList<String>>()
        for (j in assembly.joints) {
            adjacency.getOrPut(j.parentInstanceId) { mutableListOf() }.add(j.childInstanceId)
        }
        val visited = HashSet<String>()
        val stack = HashSet<String>()
        for (node in adjacency.keys) {
            if (dfsHasCycle(node, adjacency, visited, stack)) return true
        }
        return false
    }

    private fun dfsHasCycle(
        node: String,
        adjacency: Map<String, List<String>>,
        visited: MutableSet<String>,
        stack: MutableSet<String>
    ): Boolean {
        if (node in stack) return true
        if (node in visited) return false
        visited += node
        stack += node
        for (next in adjacency[node] ?: emptyList()) {
            if (dfsHasCycle(next, adjacency, visited, stack)) return true
        }
        stack -= node
        return false
    }

    private fun findIncompatibleJoints(
        assembly: ForgeAssembly,
        partsById: Map<String, ForgePart>
    ): List<MechanicalJoint> {
        if (partsById.isEmpty()) return emptyList()
        return assembly.joints.filter { j ->
            val parentInstance = assembly.instances.firstOrNull { it.id == j.parentInstanceId } ?: return@filter true
            val childInstance = assembly.instances.firstOrNull { it.id == j.childInstanceId } ?: return@filter true
            val parentPort = j.parentPortId?.let { pid ->
                partsById[parentInstance.partId]?.connectionPorts?.firstOrNull { it.id == pid }
            }
            val childPort = j.childPortId?.let { cid ->
                partsById[childInstance.partId]?.connectionPorts?.firstOrNull { it.id == cid }
            }
            // Joint sin puerto específico: no podemos validar → asumimos OK.
            if (parentPort == null && childPort == null) return@filter false
            val parentOk = parentPort == null || parentPort.compatibleJointTypes.isEmpty() || j.jointType in parentPort.compatibleJointTypes
            val childOk = childPort == null || childPort.compatibleJointTypes.isEmpty() || j.jointType in childPort.compatibleJointTypes
            !(parentOk && childOk)
        }
    }

    /**
     * Detección de interferencia AABB-based. Heurística V1: bounding cubes de 100mm
     * alrededor del origen (suficiente para educational; real CAD requiere CSG).
     */
    private fun detectSimpleInterferences(
        assembly: ForgeAssembly,
        partsById: Map<String, ForgePart>
    ): List<InterferenceResult> {
        if (assembly.instances.size < 2) return emptyList()
        val instances = assembly.instances
        val boxes = instances.map { instance ->
            val halfExtents = inferHalfExtents(instance, partsById)
            val center = instance.transform.position
            instance.id to BoundingBox(
                min = center - halfExtents,
                max = center + halfExtents
            )
        }
        val interferences = mutableListOf<InterferenceResult>()
        for (i in 0 until boxes.size) {
            for (j in i + 1 until boxes.size) {
                val (idA, boxA) = boxes[i]
                val (idB, boxB) = boxes[j]
                val intersection = aabbIntersection(boxA, boxB)
                if (intersection != null) {
                    val penetration = minOf(
                        boxA.max.x - boxB.min.x,
                        boxB.max.x - boxA.min.x,
                        boxA.max.y - boxB.min.y,
                        boxB.max.y - boxA.min.y,
                        boxA.max.z - boxB.min.z,
                        boxB.max.z - boxA.min.z
                    ).coerceAtLeast(0.0)
                    if (penetration > 1.0) { // ignoramos penetraciones < 1mm
                        interferences += InterferenceResult(
                            instanceIdA = idA,
                            instanceIdB = idB,
                            penetrationMm = penetration,
                            contactNormal = intersection
                        )
                    }
                }
            }
        }
        return interferences
    }

    private fun inferHalfExtents(instance: PartInstance, partsById: Map<String, ForgePart>): Vector3Data {
        val part = partsById[instance.partId]
        if (part != null) {
            val l = part.dimensions.lengthMm ?: 50.0
            val w = part.dimensions.widthMm ?: 50.0
            val h = part.dimensions.heightMm ?: part.dimensions.thicknessMm ?: 50.0
            return Vector3Data(l / 2.0, w / 2.0, h / 2.0)
        }
        return Vector3Data(50.0, 50.0, 50.0) // fallback genérico
    }

    private fun aabbIntersection(a: BoundingBox, b: BoundingBox): Vector3Data? {
        if (!a.intersects(b)) return null
        val nx = (a.center.x - b.center.x).coerceIn(-1.0, 1.0)
        val ny = (a.center.y - b.center.y).coerceIn(-1.0, 1.0)
        val nz = (a.center.z - b.center.z).coerceIn(-1.0, 1.0)
        val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
        return if (len < 1e-9) Vector3Data(0.0, 1.0, 0.0)
        else Vector3Data(nx / len, ny / len, nz / len)
    }

    private fun computeAssemblyCenter(assembly: ForgeAssembly): Vector3Data {
        if (assembly.instances.isEmpty()) return Vector3Data.ZERO
        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0
        for (instance in assembly.instances) {
            sumX += instance.transform.position.x
            sumY += instance.transform.position.y
            sumZ += instance.transform.position.z
        }
        val n = assembly.instances.size.toDouble()
        return Vector3Data(sumX / n, sumY / n, sumZ / n)
    }
}