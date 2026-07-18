package com.elysium369.meet.core.engine3d

import androidx.compose.ui.graphics.Color
import kotlin.math.*

enum class EngineType(val label: String) {
    INLINE_3("3 Cilindros en Línea (L3)"),
    INLINE_4("4 Cilindros en Línea (L4)"),
    INLINE_5("5 Cilindros en Línea (L5)"),
    INLINE_6("6 Cilindros en Línea (L6)"),
    V6("Motor en V6 (V6)"),
    V8("Motor en V8 (V8)"),
    V10("Motor en V10 (V10)"),
    V12("Motor en V12 (V12)"),
    BOXER_4("Boxer 4 Cilindros (H4)"),
    BOXER_6("Boxer 6 Cilindros (H6)"),
    ROTARY("Rotativo Wankel"),
    DIESEL_L4("Diesel 4 Cilindros"),
    DIESEL_V6("Diesel V6"),
    DIESEL_V8("Diesel V8"),
    HYBRID("Híbrido Paralelo/Serie"),
    PHEV("Híbrido Enchufable (PHEV)"),
    ELECTRIC("Propulsión Eléctrica (EV)")
}

/**
 * Representación matemática de un punto o vector tridimensional.
 */
data class Vector3D(val x: Float, val y: Float, val z: Float) {
    operator fun plus(v: Vector3D) = Vector3D(x + v.x, y + v.y, z + v.z)
    operator fun minus(v: Vector3D) = Vector3D(x - v.x, y - v.y, z - v.z)
    operator fun times(s: Float) = Vector3D(x * s, y * s, z * s)
    operator fun div(s: Float) = Vector3D(x / s, y / s, z / s)

    fun dot(v: Vector3D): Float = x * v.x + y * v.y + z * v.z
    fun cross(v: Vector3D) = Vector3D(
        y * v.z - z * v.y,
        z * v.x - x * v.z,
        x * v.y - y * v.x
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun normalize(): Vector3D {
        val len = length()
        return if (len > 0f) this / len else Vector3D(0f, 0f, 0f)
    }

    fun rotateX(angleRad: Float): Vector3D {
        val cos = cos(angleRad)
        val sin = sin(angleRad)
        return Vector3D(x, y * cos - z * sin, y * sin + z * cos)
    }

    fun rotateY(angleRad: Float): Vector3D {
        val cos = cos(angleRad)
        val sin = sin(angleRad)
        return Vector3D(x * cos + z * sin, y, -x * sin + z * cos)
    }

    fun rotateZ(angleRad: Float): Vector3D {
        val cos = cos(angleRad)
        val sin = sin(angleRad)
        return Vector3D(x * cos - y * sin, x * sin + y * cos, z)
    }
}

/**
 * Representa una cara poligonal tridimensional (generalmente triángulos o cuadriláteros).
 */
data class Face3D(
    val vertexIndices: List<Int>,
    val color: Color,
    val isTranslucent: Boolean = false,
    val opacity: Float = 1f,
    val customNormal: Vector3D? = null,
    val isLineOnly: Boolean = false
)

/**
 * Representa un objeto/malla 3D en el espacio.
 */
data class Mesh3D(
    val id: String,
    val name: String,
    val vertices: List<Vector3D>,
    val faces: List<Face3D>,
    val position: Vector3D = Vector3D(0f, 0f, 0f),
    val rotation: Vector3D = Vector3D(0f, 0f, 0f), // Rotaciones locales en radianes (roll, pitch, yaw)
    val scale: Vector3D = Vector3D(1f, 1f, 1f),
    val isActiveDtc: Boolean = false,
    val isHighlighted: Boolean = false
) {
    /**
     * Transforma los vértices locales del modelo al espacio universal de mundo.
     */
    fun transformToWorld(pistonOffset: Float = 0f, sparkTriggered: Boolean = false): List<Vector3D> {
        return vertices.map { v ->
            var localV = v
            if (id.startsWith("piston_") && v.y > -20f) {
                localV = Vector3D(v.x, v.y + pistonOffset, v.z)
            } else if (id.startsWith("rod_") && v.y > -10f) {
                val angleFactor = sin(pistonOffset * 0.1f) * 4f
                localV = Vector3D(v.x + angleFactor, v.y + pistonOffset, v.z)
            } else if (id.startsWith("spark_gap_") && sparkTriggered) {
                localV = Vector3D(v.x + (Math.random().toFloat() - 0.5f) * 3f, v.y, v.z + (Math.random().toFloat() - 0.5f) * 3f)
            }

            // Escala
            val scaled = Vector3D(localV.x * scale.x, localV.y * scale.y, localV.z * scale.z)

            // Rotación local
            val rotated = scaled
                .rotateZ(rotation.z)
                .rotateX(rotation.x)
                .rotateY(rotation.y)

            // Posicionamiento de mundo
            rotated + position
        }
    }
}

/** Compact semantic input for the complete proprietary catalog scene. */
data class UniversalCatalogSceneNode(
    val id: String,
    val name: String,
    val systemId: String,
    val seed: Long,
    val sectionId: String = ""
)

/**
 * Generadores procedurales para componentes en 3D.
 */
object ElysiumProceduralModels {

    fun createBox(
        id: String,
        name: String,
        width: Float,
        height: Float,
        depth: Float,
        color: Color,
        position: Vector3D = Vector3D(0f, 0f, 0f),
        rotation: Vector3D = Vector3D(0f, 0f, 0f),
        scale: Vector3D = Vector3D(1f, 1f, 1f),
        isTranslucent: Boolean = false,
        opacity: Float = 1f,
        isActiveDtc: Boolean = false
    ): Mesh3D {
        val dx = width / 2f
        val dy = height / 2f
        val dz = depth / 2f

        val vertices = listOf(
            Vector3D(-dx, -dy, -dz), // 0
            Vector3D(dx, -dy, -dz),  // 1
            Vector3D(dx, dy, -dz),   // 2
            Vector3D(-dx, dy, -dz),  // 3
            Vector3D(-dx, -dy, dz),  // 4
            Vector3D(dx, -dy, dz),   // 5
            Vector3D(dx, dy, dz),    // 6
            Vector3D(-dx, dy, dz)    // 7
        )

        val faces = listOf(
            Face3D(listOf(0, 1, 2, 3), color, isTranslucent, opacity), // Frente
            Face3D(listOf(5, 4, 7, 6), color, isTranslucent, opacity), // Atrás
            Face3D(listOf(4, 0, 3, 7), color, isTranslucent, opacity), // Izquierda
            Face3D(listOf(1, 5, 6, 2), color, isTranslucent, opacity), // Derecha
            Face3D(listOf(3, 2, 6, 7), color, isTranslucent, opacity), // Arriba
            Face3D(listOf(4, 5, 1, 0), color, isTranslucent, opacity)  // Abajo
        )

        return Mesh3D(id, name, vertices, faces, position, rotation, scale, isActiveDtc = isActiveDtc)
    }

    fun createCylinder(
        id: String,
        name: String,
        radius: Float,
        height: Float,
        segments: Int = 12,
        color: Color,
        position: Vector3D = Vector3D(0f, 0f, 0f),
        rotation: Vector3D = Vector3D(0f, 0f, 0f),
        scale: Vector3D = Vector3D(1f, 1f, 1f),
        isTranslucent: Boolean = false,
        opacity: Float = 1f,
        isActiveDtc: Boolean = false
    ): Mesh3D {
        val vertices = mutableListOf<Vector3D>()
        val faces = mutableListOf<Face3D>()
        val halfH = height / 2f

        for (i in 0 until segments) {
            val angle = (2 * PI * i / segments).toFloat()
            val x = radius * cos(angle)
            val z = radius * sin(angle)
            vertices.add(Vector3D(x, -halfH, z))
            vertices.add(Vector3D(x, halfH, z))
        }

        val centerBottomIdx = vertices.size
        vertices.add(Vector3D(0f, -halfH, 0f))
        val centerTopIdx = vertices.size
        vertices.add(Vector3D(0f, halfH, 0f))

        for (i in 0 until segments) {
            val next = (i + 1) % segments
            val b1 = i * 2
            val t1 = i * 2 + 1
            val b2 = next * 2
            val t2 = next * 2 + 1

            faces.add(Face3D(listOf(b1, b2, t2, t1), color, isTranslucent, opacity))
            faces.add(Face3D(listOf(b2, b1, centerBottomIdx), color, isTranslucent, opacity))
            faces.add(Face3D(listOf(t1, t2, centerTopIdx), color, isTranslucent, opacity))
        }

        return Mesh3D(id, name, vertices, faces, position, rotation, scale, isActiveDtc = isActiveDtc)
    }

    fun createSplineCable(
        id: String,
        name: String,
        points: List<Vector3D>,
        radius: Float,
        segments: Int = 6,
        color: Color,
        isTranslucent: Boolean = false,
        opacity: Float = 1f,
        isActiveDtc: Boolean = false
    ): Mesh3D {
        val vertices = mutableListOf<Vector3D>()
        val faces = mutableListOf<Face3D>()

        if (points.size < 2) return Mesh3D(id, name, emptyList(), emptyList())

        for (i in points.indices) {
            val p = points[i]
            val tangent = when {
                i == 0 -> (points[1] - points[0]).normalize()
                i == points.lastIndex -> (points[i] - points[i - 1]).normalize()
                else -> ((points[i + 1] - points[i]) + (points[i] - points[i - 1])).normalize()
            }

            val up = if (abs(tangent.x) < 0.9f) Vector3D(1f, 0f, 0f) else Vector3D(0f, 1f, 0f)
            val normal = tangent.cross(up).normalize()
            val binormal = tangent.cross(normal).normalize()

            for (s in 0 until segments) {
                val angle = (2 * PI * s / segments).toFloat()
                val offsetVec = (normal * cos(angle) + binormal * sin(angle)) * radius
                vertices.add(p + offsetVec)
            }
        }

        for (i in 0 until points.lastIndex) {
            val offsetThis = i * segments
            val offsetNext = (i + 1) * segments

            for (s in 0 until segments) {
                val nextS = (s + 1) % segments
                val v1 = offsetThis + s
                val v2 = offsetThis + nextS
                val v3 = offsetNext + nextS
                val v4 = offsetNext + s

                faces.add(Face3D(listOf(v1, v2, v3, v4), color, isTranslucent, opacity))
            }
        }

        return Mesh3D(id, name, vertices, faces, isActiveDtc = isActiveDtc)
    }

    /**
     * Construye la escena del Motor 3D según el tipo (L4, V6, V8 o Eléctrico).
     */
    fun buildEngineBlockScene(engineType: EngineType, activeDtcs: List<String>): List<Mesh3D> {
        if (engineType == EngineType.ELECTRIC) {
            return buildElectricMotorScene(activeDtcs)
        }

        val meshes = mutableListOf<Mesh3D>()

        // ── 1. Bloque de Cilindros Principal ──
        meshes.add(
            createBox(
                id = "engine_block",
                name = "Bloque del Motor",
                width = if (engineType == EngineType.V8) 120f else 110f,
                height = 55f,
                depth = if (engineType == EngineType.INLINE_4) 60f else 80f,
                color = Color(0xFF263238),
                position = Vector3D(0f, 0f, 0f)
            )
        )

        // ── 2. Cárter de Aceite ──
        meshes.add(
            createBox(
                id = "oil_pan",
                name = "Cárter de Aceite",
                width = 85f,
                height = 14f,
                depth = 55f,
                color = Color(0xFF15191C),
                position = Vector3D(0f, 34f, 0f),
                isActiveDtc = activeDtcs.contains("P0196") || activeDtcs.contains("P0522")
            )
        )

        // ── 3. Pistones y Bujías según la Configuración del Motor ──
        when (engineType) {
            EngineType.INLINE_4 -> {
                val cylinderXOffsets = listOf(-38f, -13f, 13f, 38f)
                meshes.add(
                    createBox(
                        id = "valve_cover",
                        name = "Tapa de Válvulas",
                        width = 100f,
                        height = 15f,
                        depth = 52f,
                        color = Color(0xFF1A1A1A),
                        position = Vector3D(0f, -35f, 0f)
                    )
                )

                cylinderXOffsets.forEachIndexed { index, xOff ->
                    addCylinderAssembly(meshes, index, Vector3D(xOff, 5f, 0f), Vector3D(0f, 0f, 0f), activeDtcs)
                }
            }
            EngineType.V6 -> {
                meshes.add(
                    createBox(
                        id = "valve_cover_b1",
                        name = "Tapa Válvulas Banco 1",
                        width = 80f,
                        height = 12f,
                        depth = 26f,
                        color = Color(0xFFD50000),
                        position = Vector3D(-22f, -30f, -15f),
                        rotation = Vector3D(0f, 0f, -0.5f)
                    )
                )
                meshes.add(
                    createBox(
                        id = "valve_cover_b2",
                        name = "Tapa Válvulas Banco 2",
                        width = 80f,
                        height = 12f,
                        depth = 26f,
                        color = Color(0xFFD50000),
                        position = Vector3D(22f, -30f, 15f),
                        rotation = Vector3D(0f, 0f, 0.5f)
                    )
                )

                val bank1Offsets = listOf(-26f, 0f, 26f)
                bank1Offsets.forEachIndexed { i, xOff ->
                    val cylinderIdx = i * 2
                    addCylinderAssembly(
                        meshes = meshes,
                        index = cylinderIdx,
                        pos = Vector3D(xOff - 12f, -6f, -12f),
                        rot = Vector3D(0f, 0f, -0.52f),
                        activeDtcs = activeDtcs
                    )
                }
                bank1Offsets.forEachIndexed { i, xOff ->
                    val cylinderIdx = i * 2 + 1
                    addCylinderAssembly(
                        meshes = meshes,
                        index = cylinderIdx,
                        pos = Vector3D(xOff + 12f, -6f, 12f),
                        rot = Vector3D(0f, 0f, 0.52f),
                        activeDtcs = activeDtcs
                    )
                }
            }
            EngineType.V8 -> {
                meshes.add(
                    createBox(
                        id = "valve_cover_b1",
                        name = "Tapa Válvulas Banco 1 (V8)",
                        width = 95f,
                        height = 12f,
                        depth = 28f,
                        color = Color(0xFF212121),
                        position = Vector3D(-24f, -30f, -18f),
                        rotation = Vector3D(0f, 0f, -0.78f)
                    )
                )
                meshes.add(
                    createBox(
                        id = "valve_cover_b2",
                        name = "Tapa Válvulas Banco 2 (V8)",
                        width = 95f,
                        height = 12f,
                        depth = 28f,
                        color = Color(0xFF212121),
                        position = Vector3D(24f, -30f, 18f),
                        rotation = Vector3D(0f, 0f, 0.78f)
                    )
                )

                val bank1Offsets = listOf(-38f, -13f, 13f, 38f)
                bank1Offsets.forEachIndexed { i, xOff ->
                    addCylinderAssembly(
                        meshes = meshes,
                        index = i * 2,
                        pos = Vector3D(xOff - 15f, -8f, -14f),
                        rot = Vector3D(0f, 0f, -0.78f),
                        activeDtcs = activeDtcs
                    )
                }
                bank1Offsets.forEachIndexed { i, xOff ->
                    addCylinderAssembly(
                        meshes = meshes,
                        index = i * 2 + 1,
                        pos = Vector3D(xOff + 15f, -8f, 14f),
                        rot = Vector3D(0f, 0f, 0.78f),
                        activeDtcs = activeDtcs
                    )
                }
            }
            else -> {}
        }

        val servicePositions = serviceCylinderPositions(engineType)
        addFuelAndIgnitionServiceLayer(meshes, servicePositions, activeDtcs)
        addIntakeAirPath(meshes, engineType, activeDtcs)
        addExhaustAftertreatment(meshes, engineType, activeDtcs)
        addCoolingAndAccessoryDrive(meshes, activeDtcs)

        // ── 4. Sensores y Alternador ──
        meshes.add(
            createBox(
                id = "ect_sensor",
                name = "Sensor ECT (Temp. Refrigerante)",
                width = 5f,
                height = 8f,
                depth = 5f,
                color = Color(0xFF00E5FF),
                position = Vector3D(-52f, -25f, 10f),
                isActiveDtc = activeDtcs.contains("P0115") || activeDtcs.contains("P0117") || activeDtcs.contains("P0118")
            )
        )
        meshes.add(
            createBox(
                id = "maf_sensor",
                name = "Sensor MAF (Flujo de Aire)",
                width = 6f,
                height = 6f,
                depth = 8f,
                color = Color(0xFFFFEB3B),
                position = Vector3D(64f, -22f, -32f),
                isActiveDtc = activeDtcs.contains("P0100") || activeDtcs.contains("P0102")
            )
        )
        meshes.add(
            createCylinder(
                id = "ckp_sensor",
                name = "Sensor CKP (Posición Cigüeñal)",
                radius = 3f,
                height = 8f,
                color = Color(0xFFE040FB),
                position = Vector3D(-42f, 25f, 25f),
                isActiveDtc = activeDtcs.contains("P0335") || activeDtcs.contains("P0336")
            )
        )
        meshes.add(
            createCylinder(
                id = "alternator",
                name = "Alternador",
                radius = 12f,
                height = 18f,
                color = Color(0xFFB0BEC5),
                position = Vector3D(-45f, 10f, -24f),
                rotation = Vector3D(PI.toFloat() / 2, 0f, 0f),
                isActiveDtc = activeDtcs.contains("P0562") || activeDtcs.contains("P0563")
            )
        )

        return meshes
    }

    private fun serviceCylinderPositions(engineType: EngineType): List<Pair<Int, Vector3D>> {
        return when (engineType) {
            EngineType.INLINE_3 -> listOf(-25f, 0f, 25f).mapIndexed { index, x ->
                index to Vector3D(x, 5f, 0f)
            }
            EngineType.INLINE_4, EngineType.DIESEL_L4 -> listOf(-38f, -13f, 13f, 38f).mapIndexed { index, x ->
                index to Vector3D(x, 5f, 0f)
            }
            EngineType.INLINE_5 -> listOf(-50f, -25f, 0f, 25f, 50f).mapIndexed { index, x ->
                index to Vector3D(x, 5f, 0f)
            }
            EngineType.INLINE_6 -> listOf(-62f, -38f, -13f, 13f, 38f, 62f).mapIndexed { index, x ->
                index to Vector3D(x, 5f, 0f)
            }
            EngineType.V6, EngineType.DIESEL_V6 -> listOf(-26f, 0f, 26f).flatMapIndexed { i, x ->
                listOf(
                    (i * 2) to Vector3D(x - 12f, -6f, -12f),
                    (i * 2 + 1) to Vector3D(x + 12f, -6f, 12f)
                )
            }
            EngineType.V8, EngineType.DIESEL_V8 -> listOf(-38f, -13f, 13f, 38f).flatMapIndexed { i, x ->
                listOf(
                    (i * 2) to Vector3D(x - 15f, -8f, -14f),
                    (i * 2 + 1) to Vector3D(x + 15f, -8f, 14f)
                )
            }
            EngineType.V10 -> listOf(-50f, -25f, 0f, 25f, 50f).flatMapIndexed { i, x ->
                listOf(
                    (i * 2) to Vector3D(x - 15f, -8f, -14f),
                    (i * 2 + 1) to Vector3D(x + 15f, -8f, 14f)
                )
            }
            EngineType.V12 -> listOf(-62f, -38f, -13f, 13f, 38f, 62f).flatMapIndexed { i, x ->
                listOf(
                    (i * 2) to Vector3D(x - 15f, -8f, -14f),
                    (i * 2 + 1) to Vector3D(x + 15f, -8f, 14f)
                )
            }
            EngineType.BOXER_4 -> listOf(-25f, 25f).flatMapIndexed { i, x ->
                listOf(
                    (i * 2) to Vector3D(x, -4f, -18f),
                    (i * 2 + 1) to Vector3D(x, -4f, 18f)
                )
            }
            EngineType.BOXER_6 -> listOf(-32f, 0f, 32f).flatMapIndexed { i, x ->
                listOf(
                    (i * 2) to Vector3D(x, -4f, -18f),
                    (i * 2 + 1) to Vector3D(x, -4f, 18f)
                )
            }
            EngineType.ROTARY -> listOf(
                0 to Vector3D(-15f, 0f, 0f),
                1 to Vector3D(15f, 0f, 0f)
            )
            EngineType.HYBRID, EngineType.PHEV -> listOf(-38f, -13f, 13f, 38f).mapIndexed { index, x ->
                index to Vector3D(x, 5f, 0f)
            }
            EngineType.ELECTRIC -> emptyList()
        }
    }

    private fun buildElectricMotorScene(activeDtcs: List<String>): List<Mesh3D> {
        val meshes = mutableListOf<Mesh3D>()

        meshes.add(
            createCylinder(
                id = "electric_motor",
                name = "Motor de Tracción Eléctrico",
                radius = 32f,
                height = 55f,
                color = Color(0xFF37474F),
                position = Vector3D(-25f, 5f, 0f),
                rotation = Vector3D(0f, 0f, PI.toFloat() / 2f),
                isActiveDtc = activeDtcs.contains("P0A90")
            )
        )
        meshes.add(
            createCylinder(
                id = "stator_windings",
                name = "Devanados del Estator (Cobre)",
                radius = 28f,
                height = 51f,
                color = Color(0xFFFF9100).copy(alpha = 0.8f),
                position = Vector3D(-25f, 5f, 0f),
                rotation = Vector3D(0f, 0f, PI.toFloat() / 2f),
                isTranslucent = true,
                opacity = 0.5f
            )
        )

        meshes.add(
            createBox(
                id = "inverter_module",
                name = "Inversor de Potencia DC/AC",
                width = 52f,
                height = 25f,
                depth = 48f,
                color = Color(0xFFB0BEC5),
                position = Vector3D(-25f, -32f, 0f),
                isActiveDtc = activeDtcs.contains("P0A78")
            )
        )

        meshes.add(
            createBox(
                id = "hv_battery_pack",
                name = "Paquete de Baterías de Litio (HV)",
                width = 110f,
                height = 10f,
                depth = 95f,
                color = Color(0xFF263238),
                position = Vector3D(40f, 32f, 0f),
                isActiveDtc = activeDtcs.contains("P0A80")
            )
        )
        for (i in 0..2) {
            meshes.add(
                createBox(
                    id = "battery_module_$i",
                    name = "Módulo de Celdas ${i + 1}",
                    width = 30f,
                    height = 6f,
                    depth = 85f,
                    color = Color(0xFF00FFD4).copy(alpha = 0.6f),
                    position = Vector3D(10f + i * 32f, 32f, 0f),
                    isTranslucent = true,
                    opacity = 0.6f
                )
            )
        }

        meshes.add(
            createBox(
                id = "bms_module",
                name = "Sistema de Gestión de Batería (BMS)",
                width = 20f,
                height = 8f,
                depth = 25f,
                color = Color(0xFF00E5FF),
                position = Vector3D(-35f, 32f, 30f),
                isActiveDtc = activeDtcs.contains("P0ABC")
            )
        )

        return meshes
    }

    private fun addCylinderAssembly(
        meshes: MutableList<Mesh3D>,
        index: Int,
        pos: Vector3D,
        rot: Vector3D,
        activeDtcs: List<String>
    ) {
        meshes.add(
            createCylinder(
                id = "piston_$index",
                name = "Pistón ${index + 1}",
                radius = 8f,
                height = 9f,
                color = Color(0xFF78909C),
                position = pos,
                rotation = rot
            )
        )
        meshes.add(
            createBox(
                id = "rod_$index",
                name = "Biela ${index + 1}",
                width = 2.5f,
                height = 20f,
                depth = 1.8f,
                color = Color(0xFF546E7A),
                position = pos + Vector3D(0f, 12f, 0f).rotateZ(rot.z),
                rotation = rot
            )
        )
        val isSparkDtc = activeDtcs.contains("P030${index + 1}") || activeDtcs.contains("P0300")
        meshes.add(
            createCylinder(
                id = "spark_plug_$index",
                name = "Bujía ${index + 1}",
                radius = 2.2f,
                height = 11f,
                color = Color(0xFFECEFF1),
                position = pos - Vector3D(0f, 22f, 0f).rotateZ(rot.z),
                rotation = rot,
                isActiveDtc = isSparkDtc
            )
        )
        meshes.add(
            Mesh3D(
                id = "spark_gap_$index",
                name = "Arco Eléctrico Bujía ${index + 1}",
                vertices = listOf(
                    pos - Vector3D(0f, 16f, 0f).rotateZ(rot.z),
                    pos - Vector3D(-1f, 13f, 1f).rotateZ(rot.z),
                    pos - Vector3D(1f, 11f, -1f).rotateZ(rot.z),
                    pos - Vector3D(0f, 9f, 0f).rotateZ(rot.z)
                ),
                faces = listOf(
                    Face3D(listOf(0, 1), Color(0xFF00E5FF), isTranslucent = true, isLineOnly = true),
                    Face3D(listOf(1, 2), Color(0xFF00E5FF), isTranslucent = true, isLineOnly = true),
                    Face3D(listOf(2, 3), Color(0xFF00E5FF), isTranslucent = true, isLineOnly = true)
                )
            )
        )
    }

    private fun addFuelAndIgnitionServiceLayer(
        meshes: MutableList<Mesh3D>,
        cylinderPositions: List<Pair<Int, Vector3D>>,
        activeDtcs: List<String>
    ) {
        if (cylinderPositions.isEmpty()) return

        cylinderPositions.forEach { (index, pos) ->
            val cylinderNumber = index + 1
            val coilDtc = activeDtcs.contains("P035$cylinderNumber") ||
                (cylinderNumber == 1 && activeDtcs.contains("P0351")) ||
                (cylinderNumber == 2 && activeDtcs.contains("P0352"))
            meshes.add(
                createBox(
                    id = "ignition_coil_$index",
                    name = "Bobina COP $cylinderNumber",
                    width = 8f,
                    height = 12f,
                    depth = 8f,
                    color = Color(0xFF263238),
                    position = pos - Vector3D(0f, 35f, 0f),
                    isActiveDtc = coilDtc || activeDtcs.contains("P030$cylinderNumber")
                )
            )
            meshes.add(
                createCylinder(
                    id = "injector_$index",
                    name = "Inyector $cylinderNumber",
                    radius = 2.5f,
                    height = 13f,
                    segments = 10,
                    color = Color(0xFF00C853),
                    position = pos + Vector3D(0f, -20f, -18f),
                    rotation = Vector3D(PI.toFloat() / 2f, 0f, 0f),
                    isActiveDtc = activeDtcs.contains("P020$cylinderNumber")
                )
            )
        }

        val first = cylinderPositions.minBy { it.second.x }.second
        val last = cylinderPositions.maxBy { it.second.x }.second
        val railCenter = Vector3D((first.x + last.x) / 2f, -17f, -22f)
        meshes.add(
            createCylinder(
                id = "fuel_rail",
                name = "Riel de Combustible",
                radius = 3.2f,
                height = (last.x - first.x).absoluteValue + 22f,
                segments = 12,
                color = Color(0xFFB0BEC5),
                position = railCenter,
                rotation = Vector3D(0f, 0f, PI.toFloat() / 2f),
                isActiveDtc = activeDtcs.contains("P0087") || activeDtcs.contains("P0191")
            )
        )
        meshes.add(
            createCylinder(
                id = "fuel_pressure_sensor",
                name = "Sensor Presión Combustible",
                radius = 3f,
                height = 6f,
                segments = 10,
                color = Color(0xFFFFEA00),
                position = railCenter + Vector3D((last.x - first.x) / 2f + 8f, 0f, 0f),
                isActiveDtc = activeDtcs.contains("P0190") || activeDtcs.contains("P0191")
            )
        )
    }

    private fun addIntakeAirPath(
        meshes: MutableList<Mesh3D>,
        engineType: EngineType,
        activeDtcs: List<String>
    ) {
        val manifoldWidth = if (engineType == EngineType.INLINE_4) 80f else 92f
        meshes.add(
            createBox(
                id = "intake_manifold",
                name = "Múltiple de Admisión",
                width = manifoldWidth,
                height = 14f,
                depth = 18f,
                color = Color(0xFF455A64),
                position = Vector3D(5f, -42f, -34f),
                isActiveDtc = activeDtcs.contains("P0105") || activeDtcs.contains("P0171") || activeDtcs.contains("P0174")
            )
        )
        listOf(-36f, -12f, 12f, 36f).forEachIndexed { index, x ->
            meshes.add(
                createSplineCable(
                    id = "intake_runner_$index",
                    name = "Conducto Admisión ${index + 1}",
                    points = listOf(
                        Vector3D(x, -35f, -27f),
                        Vector3D(x * 0.8f, -22f, -17f),
                        Vector3D(x * 0.65f, -8f, -7f)
                    ),
                    radius = 4.5f,
                    segments = 8,
                    color = Color(0xFF607D8B),
                    isTranslucent = true,
                    opacity = 0.72f
                )
            )
        }
        meshes.add(
            createCylinder(
                id = "throttle_body",
                name = "Cuerpo de Aceleración",
                radius = 10f,
                height = 12f,
                segments = 16,
                color = Color(0xFF90A4AE),
                position = Vector3D(70f, -43f, -34f),
                rotation = Vector3D(PI.toFloat() / 2f, 0f, 0f),
                isActiveDtc = activeDtcs.contains("P0121") || activeDtcs.contains("P2119") || activeDtcs.contains("P2135")
            )
        )
        meshes.add(
            createBox(
                id = "throttle_plate",
                name = "Mariposa de Aceleración",
                width = 1.6f,
                height = 15f,
                depth = 15f,
                color = Color(0xFFCFD8DC),
                position = Vector3D(70f, -43f, -34f),
                rotation = Vector3D(0.25f, 0f, 0.55f)
            )
        )
        meshes.add(
            createCylinder(
                id = "air_intake_duct",
                name = "Ducto de Admisión",
                radius = 8f,
                height = 44f,
                segments = 14,
                color = Color(0xFF1B242B),
                position = Vector3D(94f, -43f, -34f),
                rotation = Vector3D(0f, PI.toFloat() / 2f, 0f)
            )
        )
        meshes.add(
            createBox(
                id = "map_sensor",
                name = "Sensor MAP",
                width = 8f,
                height = 5f,
                depth = 6f,
                color = Color(0xFF00E5FF),
                position = Vector3D(16f, -53f, -35f),
                isActiveDtc = activeDtcs.contains("P0105") || activeDtcs.contains("P0107")
            )
        )
        meshes.add(
            createBox(
                id = "iat_sensor",
                name = "Sensor IAT",
                width = 6f,
                height = 5f,
                depth = 5f,
                color = Color(0xFF18FFFF),
                position = Vector3D(88f, -53f, -34f),
                isActiveDtc = activeDtcs.contains("P0110") || activeDtcs.contains("P0112") || activeDtcs.contains("P0113")
            )
        )
    }

    private fun addExhaustAftertreatment(
        meshes: MutableList<Mesh3D>,
        engineType: EngineType,
        activeDtcs: List<String>
    ) {
        val runnerXs = if (engineType == EngineType.INLINE_4) listOf(-38f, -13f, 13f, 38f) else listOf(-48f, -24f, 0f, 24f, 48f)
        runnerXs.forEachIndexed { index, x ->
            meshes.add(
                createSplineCable(
                    id = "exhaust_runner_$index",
                    name = "Ramal Escape ${index + 1}",
                    points = listOf(
                        Vector3D(x, -4f, 28f),
                        Vector3D(x * 0.75f, 8f, 43f),
                        Vector3D(x * 0.45f, 18f, 50f)
                    ),
                    radius = 3.8f,
                    segments = 8,
                    color = Color(0xFF795548),
                    isActiveDtc = activeDtcs.contains("P0420") || activeDtcs.contains("P0430")
                )
            )
        }
        meshes.add(
            createCylinder(
                id = "exhaust_manifold",
                name = "Múltiple de Escape",
                radius = 5f,
                height = 78f,
                segments = 12,
                color = Color(0xFF5D4037),
                position = Vector3D(0f, 20f, 52f),
                rotation = Vector3D(0f, 0f, PI.toFloat() / 2f)
            )
        )
        meshes.add(
            createCylinder(
                id = "o2_upstream",
                name = "Sensor O2 Pre-Catalizador",
                radius = 2.5f,
                height = 12f,
                segments = 10,
                color = Color(0xFFECEFF1),
                position = Vector3D(28f, 11f, 58f),
                rotation = Vector3D(0.8f, 0f, 0f),
                isActiveDtc = activeDtcs.contains("P0130") || activeDtcs.contains("P0135")
            )
        )
        meshes.add(
            createCylinder(
                id = "catalytic_converter",
                name = "Catalizador",
                radius = 12f,
                height = 34f,
                segments = 16,
                color = Color(0xFFBCAAA4),
                position = Vector3D(58f, 24f, 55f),
                rotation = Vector3D(0f, PI.toFloat() / 2f, 0f),
                isActiveDtc = activeDtcs.contains("P0420") || activeDtcs.contains("P0430")
            )
        )
        meshes.add(
            createCylinder(
                id = "o2_downstream",
                name = "Sensor O2 Post-Catalizador",
                radius = 2.4f,
                height = 11f,
                segments = 10,
                color = Color(0xFFECEFF1),
                position = Vector3D(78f, 16f, 58f),
                rotation = Vector3D(0.8f, 0f, 0f),
                isActiveDtc = activeDtcs.contains("P0136") || activeDtcs.contains("P0141")
            )
        )
    }

    private fun addCoolingAndAccessoryDrive(
        meshes: MutableList<Mesh3D>,
        activeDtcs: List<String>
    ) {
        meshes.add(
            createCylinder(
                id = "water_pump",
                name = "Bomba de Agua",
                radius = 10f,
                height = 10f,
                segments = 16,
                color = Color(0xFF607D8B),
                position = Vector3D(-67f, 5f, -8f),
                rotation = Vector3D(PI.toFloat() / 2f, 0f, 0f),
                isActiveDtc = activeDtcs.contains("P0128") || activeDtcs.contains("P0217")
            )
        )
        meshes.add(
            createBox(
                id = "thermostat_housing",
                name = "Carcasa Termostato",
                width = 14f,
                height = 11f,
                depth = 14f,
                color = Color(0xFF546E7A),
                position = Vector3D(-62f, -20f, 16f),
                isActiveDtc = activeDtcs.contains("P0128")
            )
        )
        meshes.add(
            createCylinder(
                id = "oil_filter",
                name = "Filtro de Aceite",
                radius = 7f,
                height = 15f,
                segments = 14,
                color = Color(0xFF1565C0),
                position = Vector3D(42f, 26f, -25f),
                rotation = Vector3D(PI.toFloat() / 2f, 0f, 0f),
                isActiveDtc = activeDtcs.contains("P0520") || activeDtcs.contains("P0522")
            )
        )
        meshes.add(
            Mesh3D(
                id = "serpentine_belt",
                name = "Banda Serpentina",
                vertices = listOf(
                    Vector3D(-45f, 10f, -24f),
                    Vector3D(-67f, 5f, -8f),
                    Vector3D(-42f, 30f, 8f),
                    Vector3D(-45f, 10f, -24f)
                ),
                faces = listOf(
                    Face3D(listOf(0, 1, 2, 3), Color(0xFF111111), isTranslucent = true, opacity = 0.9f, isLineOnly = true)
                )
            )
        )
        meshes.add(
            createCylinder(
                id = "camshaft_sensor",
                name = "Sensor CMP Árbol de Levas",
                radius = 3f,
                height = 8f,
                color = Color(0xFFE040FB),
                position = Vector3D(42f, -31f, 23f),
                isActiveDtc = activeDtcs.contains("P0340") || activeDtcs.contains("P0341")
            )
        )
    }

    private data class FuseBaySpec(
        val id: String,
        val slot: String,
        val type: String,
        val amps: Int,
        val function: String,
        val position: Vector3D,
        val relatedDtcs: List<String> = emptyList()
    )

    private data class RelayBaySpec(
        val id: String,
        val label: String,
        val position: Vector3D,
        val relatedDtcs: List<String> = emptyList()
    )

    private fun fuseColorForAmps(amps: Int): Color {
        return when (amps) {
            5 -> Color(0xFFD7CCC8)
            7 -> Color(0xFF8D6E63)
            10 -> Color(0xFFFF1744)
            15 -> Color(0xFF2979FF)
            20 -> Color(0xFFFFEA00)
            25 -> Color(0xFFECEFF1)
            30 -> Color(0xFF00C853)
            40 -> Color(0xFFFF9100)
            50 -> Color(0xFFE53935)
            60 -> Color(0xFF1E88E5)
            80 -> Color(0xFFFFB300)
            100 -> Color(0xFF66BB6A)
            else -> Color(0xFFB0BEC5)
        }
    }

    fun buildRelayFuseBoxScene(engineType: EngineType, activeDtcs: List<String>): List<Mesh3D> {
        val meshes = mutableListOf<Mesh3D>()

        val isEv = engineType == EngineType.ELECTRIC

        meshes.add(
            createBox(
                id = "fuse_box_housing",
                name = "Carcasa ABS de Caja de Fusibles",
                width = 156f,
                height = 28f,
                depth = 108f,
                color = Color(0xFF10151B),
                position = Vector3D(0f, 0f, 0f)
            )
        )
        meshes.add(
            createBox(
                id = "fuse_box_tray",
                name = "Bandeja Recesada de Fusibles",
                width = 146f,
                height = 4f,
                depth = 98f,
                color = Color(0xFF2A2E35),
                position = Vector3D(0f, -14f, 0f)
            )
        )
        meshes.add(
            createBox(
                id = "fuse_box_pcb",
                name = "PCB de Distribución",
                width = 134f,
                height = 2.8f,
                depth = 88f,
                color = Color(0xFF0B3D2E),
                position = Vector3D(0f, -17.5f, 0f),
                isTranslucent = true,
                opacity = 0.82f
            )
        )
        meshes.add(
            createBox(
                id = "fuse_box_lid",
                name = "Tapa Translúcida con Diagrama",
                width = 164f,
                height = 6f,
                depth = 116f,
                color = Color(0xFF263238),
                position = Vector3D(0f, -43f, 0f),
                isTranslucent = true,
                opacity = 0.22f
            )
        )

        listOf(-78f to -54f, 78f to -54f, -78f to 54f, 78f to 54f).forEachIndexed { index, (x, z) ->
            meshes.add(
                createCylinder(
                    id = "fuse_box_screw_$index",
                    name = "Tornillo Torx Caja ${index + 1}",
                    radius = 3.2f,
                    height = 3f,
                    segments = 14,
                    color = Color(0xFFB0BEC5),
                    position = Vector3D(x, -24f, z)
                )
            )
        }

        listOf(
            Vector3D(-84f, -17f, 0f),
            Vector3D(84f, -17f, 0f),
            Vector3D(0f, -17f, -58f),
            Vector3D(0f, -17f, 58f)
        ).forEachIndexed { index, pos ->
            meshes.add(
                createBox(
                    id = "fuse_box_latch_$index",
                    name = "Pestaña de Retención ${index + 1}",
                    width = if (index < 2) 5f else 28f,
                    height = 8f,
                    depth = if (index < 2) 24f else 5f,
                    color = Color(0xFF05070A),
                    position = pos
                )
            )
        }

        listOf(
            Triple("bus_bar_battery", "Barra B+ Permanente", Vector3D(-48f, -19.5f, 42f)),
            Triple("bus_bar_acc", "Barra ACC/IGN", Vector3D(12f, -19.5f, 42f)),
            Triple("bus_bar_ecm", "Barra ECM/PCM", Vector3D(52f, -19.5f, -16f))
        ).forEach { (id, name, pos) ->
            meshes.add(
                createBox(
                    id = id,
                    name = name,
                    width = 52f,
                    height = 2.4f,
                    depth = 4f,
                    color = Color(0xFFC77827),
                    position = pos
                )
            )
        }

        listOf(
            Vector3D(-74f, -22f, 48f),
            Vector3D(74f, -22f, 48f),
            Vector3D(-74f, -22f, -48f),
            Vector3D(74f, -22f, -48f)
        ).forEachIndexed { index, pos ->
            meshes.add(
                createBox(
                    id = "main_connector_$index",
                    name = "Conector Inferior C${index + 1}",
                    width = 20f,
                    height = 14f,
                    depth = 10f,
                    color = Color(0xFF111820),
                    position = pos
                )
            )
            meshes.add(
                createSplineCable(
                    id = "main_harness_branch_$index",
                    name = "Ramal de Arnés C${index + 1}",
                    points = listOf(
                        pos + Vector3D(0f, 8f, 0f),
                        pos + Vector3D(if (pos.x < 0) -16f else 16f, 18f, if (pos.z < 0) -20f else 20f),
                        pos + Vector3D(if (pos.x < 0) -42f else 42f, 28f, if (pos.z < 0) -34f else 34f)
                    ),
                    radius = 2.8f,
                    segments = 8,
                    color = Color(0xFF0A0D11)
                )
            )
        }

        if (isEv) {
            meshes.add(
                createCylinder(
                    id = "contactor_positive",
                    name = "Contactor HV (+)",
                    radius = 12f,
                    height = 20f,
                    color = Color(0xFFD50000),
                    position = Vector3D(-35f, -24f, -20f),
                    isActiveDtc = activeDtcs.contains("P0AA1")
                )
            )
            meshes.add(
                createCylinder(
                    id = "contactor_negative",
                    name = "Contactor HV (-)",
                    radius = 12f,
                    height = 20f,
                    color = Color(0xFF2979FF),
                    position = Vector3D(-5f, -24f, -20f),
                    isActiveDtc = activeDtcs.contains("P0AA4")
                )
            )
            meshes.add(
                createBox(
                    id = "safety_disconnect",
                    name = "Enchufe de Desconexión de Servicio (MSD)",
                    width = 24f,
                    height = 16f,
                    depth = 26f,
                    color = Color(0xFFFF9100),
                    position = Vector3D(35f, -22f, 15f),
                    isActiveDtc = activeDtcs.contains("P0A0D")
                )
            )
            meshes.add(
                createBox(
                    id = "hv_main_fuse",
                    name = "Fusible Principal HV (400V 150A)",
                    width = 30f,
                    height = 10f,
                    depth = 12f,
                    color = Color(0xFFFFEB3B),
                    position = Vector3D(35f, -21f, -20f),
                    isActiveDtc = activeDtcs.contains("P0A09")
                )
            )
            return meshes
        }

        val relayGrid = listOf(
            RelayBaySpec("relay_fuel_pump", "Relé Bomba Gasolina", Vector3D(-52f, -30f, -31f), listOf("P0230", "P0087")),
            RelayBaySpec("relay_starter", "Relé Motor Arranque", Vector3D(-18f, -30f, -31f), listOf("P0512")),
            RelayBaySpec("relay_ignition", "Relé Principal ECM", Vector3D(16f, -30f, -31f), listOf("P0685", "P0603")),
            RelayBaySpec("relay_fan", "Relé Ventilador", Vector3D(50f, -30f, -31f), listOf("P0480", "P0481")),
            RelayBaySpec("relay_ac_clutch", "Relé Compresor A/C", Vector3D(-52f, -30f, 31f), listOf("P0645")),
            RelayBaySpec("relay_headlamp", "Relé Faros", Vector3D(-18f, -30f, 31f)),
            RelayBaySpec("relay_horn", "Relé Bocina", Vector3D(16f, -30f, 31f)),
            RelayBaySpec("relay_aux", "Relé Auxiliar", Vector3D(50f, -30f, 31f))
        )

        relayGrid.forEach { relay ->
            val isFailing = relay.relatedDtcs.any { activeDtcs.contains(it) }

            meshes.add(
                createBox(
                    id = relay.id,
                    name = relay.label,
                    width = 25f,
                    height = 24f,
                    depth = 25f,
                    color = Color(0xFF171B22),
                    position = relay.position,
                    isActiveDtc = isFailing
                )
            )
            meshes.add(
                createBox(
                    id = "${relay.id}_engraving",
                    name = "Grabado ${relay.label}",
                    width = 17f,
                    height = 1.2f,
                    depth = 2f,
                    color = Color(0xFF607D8B),
                    position = relay.position + Vector3D(0f, -12.6f, -9f)
                )
            )
            listOf(-7f, 0f, 7f).forEachIndexed { index, x ->
                meshes.add(
                    createBox(
                        id = "${relay.id}_pin_$index",
                        name = "Terminal ${index + 1} ${relay.label}",
                        width = 2.2f,
                        height = 8f,
                        depth = 5f,
                        color = Color(0xFFE6C16A),
                        position = relay.position + Vector3D(x, 14f, 8f)
                    )
                )
            }
        }

        val fuseGrid = listOf(
            FuseBaySpec("fuse_ecm_batt", "F1", "Micro2", 10, "ECM memoria permanente", Vector3D(-64f, -29f, -4f), listOf("P0603", "P0685")),
            FuseBaySpec("fuse_ecm_ign", "F2", "Micro2", 15, "ECM ignición/ACC", Vector3D(-52f, -29f, -4f), listOf("P0685")),
            FuseBaySpec("fuse_injectors", "F3", "Mini", 15, "Inyectores", Vector3D(-40f, -29f, -4f), listOf("P0201", "P0202", "P0203", "P0204")),
            FuseBaySpec("fuse_ignition_coils", "F4", "Mini", 20, "Bobinas de encendido", Vector3D(-28f, -29f, -4f), listOf("P0351", "P0352", "P0300")),
            FuseBaySpec("fuse_o2_heater", "F5", "Micro2", 15, "Calentadores O2", Vector3D(-16f, -29f, -4f), listOf("P0135", "P0141")),
            FuseBaySpec("fuse_maf_map", "F6", "Micro2", 10, "Sensores MAF/MAP/IAT", Vector3D(-4f, -29f, -4f), listOf("P0100", "P0105", "P0110")),
            FuseBaySpec("fuse_fuel_pump", "F7", "Mini", 20, "Bomba de combustible", Vector3D(8f, -29f, -4f), listOf("P0230", "P0087")),
            FuseBaySpec("fuse_cooling_fan_low", "F8", "JCASE", 30, "Ventilador baja", Vector3D(25f, -29f, -4f), listOf("P0480")),
            FuseBaySpec("fuse_cooling_fan_high", "F9", "JCASE", 40, "Ventilador alta", Vector3D(45f, -29f, -4f), listOf("P0481")),
            FuseBaySpec("fuse_starter", "F10", "Maxi", 40, "Solenoide arranque", Vector3D(66f, -29f, -4f), listOf("P0512")),
            FuseBaySpec("fuse_abs", "F11", "JCASE", 30, "Módulo ABS", Vector3D(-62f, -29f, 15f), listOf("C0035", "C0040")),
            FuseBaySpec("fuse_eps", "F12", "PAL", 60, "Dirección EPS", Vector3D(-36f, -29f, 17f), listOf("C1604")),
            FuseBaySpec("fuse_ac_clutch", "F13", "Mini", 10, "Compresor A/C", Vector3D(-11f, -29f, 15f), listOf("P0645")),
            FuseBaySpec("fuse_obd_dlc", "F14", "Micro2", 10, "Puerto OBD-II DLC", Vector3D(1f, -29f, 15f), listOf("U0100")),
            FuseBaySpec("fuse_headlamp", "F15", "Mini", 15, "Faros principales", Vector3D(13f, -29f, 15f)),
            FuseBaySpec("fuse_blower", "F16", "JCASE", 40, "Soplador HVAC", Vector3D(31f, -29f, 15f)),
            FuseBaySpec("fuse_battery_main", "F17", "PAL", 80, "Alimentación principal B+", Vector3D(59f, -29f, 18f), listOf("P0562", "P0563"))
        )

        fuseGrid.forEach { fuse ->
            val isBlown = fuse.relatedDtcs.any { activeDtcs.contains(it) }
            val fuseColor = if (isBlown) Color(0xFFD50000) else fuseColorForAmps(fuse.amps)
            val shell = when (fuse.type) {
                "Micro2" -> Vector3D(7f, 10f, 5f)
                "Mini" -> Vector3D(9f, 12f, 6f)
                "JCASE" -> Vector3D(16f, 13f, 10f)
                "Maxi" -> Vector3D(20f, 16f, 10f)
                "PAL" -> Vector3D(26f, 10f, 9f)
                else -> Vector3D(8f, 11f, 6f)
            }

            meshes.add(
                createBox(
                    id = "socket_${fuse.id}",
                    name = "Zócalo ${fuse.slot} ${fuse.function}",
                    width = shell.x + 4f,
                    height = 3f,
                    depth = shell.z + 4f,
                    color = Color(0xFF080A0D),
                    position = fuse.position + Vector3D(0f, 7.2f, 0f)
                )
            )
            meshes.add(
                createBox(
                    id = fuse.id,
                    name = "${fuse.slot} ${fuse.type} ${fuse.amps}A - ${fuse.function}",
                    width = shell.x,
                    height = shell.y,
                    depth = shell.z,
                    color = fuseColor,
                    position = fuse.position,
                    isTranslucent = true,
                    opacity = if (isBlown) 0.95f else 0.78f,
                    isActiveDtc = isBlown
                )
            )
            meshes.add(
                createBox(
                    id = "${fuse.id}_amp_band",
                    name = "Grabado ${fuse.slot} ${fuse.amps}A",
                    width = shell.x * 0.72f,
                    height = 1.1f,
                    depth = 1.5f,
                    color = Color.White.copy(alpha = 0.7f),
                    position = fuse.position + Vector3D(0f, -shell.y / 2f - 0.7f, -shell.z / 2f + 1.2f),
                    isTranslucent = true,
                    opacity = 0.7f
                )
            )
            listOf(-shell.x * 0.23f, shell.x * 0.23f).forEachIndexed { bladeIndex, x ->
                meshes.add(
                    createBox(
                        id = "${fuse.id}_blade_$bladeIndex",
                        name = "Terminal ${bladeIndex + 1} ${fuse.slot}",
                        width = 1.8f,
                        height = 8f,
                        depth = shell.z * 0.55f,
                        color = Color(0xFFE6C16A),
                        position = fuse.position + Vector3D(x, 8.5f, 0f)
                    )
                )
            }

            val elementColor = if (isBlown) Color(0xFF2B1510) else Color(0xFFE0E0E0)
            meshes.add(
                Mesh3D(
                    id = "${fuse.id}_element",
                    name = if (isBlown) "Elemento abierto ${fuse.slot}" else "Elemento continuo ${fuse.slot}",
                    vertices = listOf(
                        fuse.position + Vector3D(-shell.x * 0.28f, -1.5f, -shell.z / 2f - 0.4f),
                        fuse.position + Vector3D(0f, if (isBlown) 1.6f else -3.2f, -shell.z / 2f - 0.4f),
                        fuse.position + Vector3D(shell.x * 0.28f, -1.5f, -shell.z / 2f - 0.4f)
                    ),
                    faces = listOf(
                        Face3D(listOf(0, 1, 2), elementColor, isTranslucent = true, opacity = 0.9f, isLineOnly = true)
                    )
                )
            )
            if (isBlown) {
                meshes.add(
                    createBox(
                        id = "${fuse.id}_carbon",
                        name = "Carbonización ${fuse.slot}",
                        width = shell.x * 0.72f,
                        height = 1.4f,
                        depth = 1.8f,
                        color = Color(0xFF1B0D09),
                        position = fuse.position + Vector3D(0f, -shell.y / 2f - 1.2f, 0f)
                    )
                )
            }
        }

        return meshes
    }

    fun buildWiringHarnessScene(engineType: EngineType, activeDtcs: List<String>): List<Mesh3D> {
        val meshes = mutableListOf<Mesh3D>()

        if (engineType == EngineType.ELECTRIC) {
            val battDtc = activeDtcs.contains("P0A80")
            meshes.add(
                createSplineCable(
                    id = "wire_hv_battery_to_inverter",
                    name = "Cables de Potencia HV (Batería a Inversor)",
                    points = listOf(
                        Vector3D(40f, 28f, 0f),
                        Vector3D(0f, 25f, 15f),
                        Vector3D(-25f, -20f, 0f)
                    ),
                    radius = 4f,
                    color = if (battDtc) Color(0xFFFF3D00) else Color(0xFFFF6D00),
                    isActiveDtc = battDtc
                )
            )

            val motorDtc = activeDtcs.contains("P0A90")
            meshes.add(
                createSplineCable(
                    id = "wire_hv_inverter_to_motor",
                    name = "Cables Trifásicos HV (Inversor a Motor)",
                    points = listOf(
                        Vector3D(-25f, -20f, 0f),
                        Vector3D(-25f, 5f, 0f)
                    ),
                    radius = 3.5f,
                    color = if (motorDtc) Color(0xFFFF3D00) else Color(0xFFFF6D00),
                    isActiveDtc = motorDtc
                )
            )
            return meshes
        }

        meshes.add(
            createSplineCable(
                id = "harness_trunk",
                name = "Arnés Eléctrico Principal",
                points = listOf(
                    Vector3D(-60f, 30f, -20f),
                    Vector3D(-30f, 25f, -22f),
                    Vector3D(0f, 20f, -20f),
                    Vector3D(30f, 25f, -18f),
                    Vector3D(60f, 30f, -20f)
                ),
                radius = 6f,
                color = Color(0xFF151515)
            )
        )

        val ectDtc = activeDtcs.contains("P0115")
        meshes.add(
            createSplineCable(
                id = "wire_ect",
                name = "Línea de Señal ECT",
                points = listOf(
                    Vector3D(-52f, -21f, 10f),
                    Vector3D(-48f, 5f, 0f),
                    Vector3D(-30f, 25f, -22f)
                ),
                radius = 1.5f,
                color = if (ectDtc) Color(0xFF00E5FF) else Color(0xFF006064),
                isActiveDtc = ectDtc
            )
        )

        val mafDtc = activeDtcs.contains("P0100")
        meshes.add(
            createSplineCable(
                id = "wire_maf",
                name = "Línea de Señal MAF",
                points = listOf(
                    Vector3D(64f, -19f, -32f),
                    Vector3D(50f, 10f, -25f),
                    Vector3D(30f, 25f, -18f)
                ),
                radius = 1.5f,
                color = if (mafDtc) Color(0xFFFFEB3B) else Color(0xFFF57F17),
                isActiveDtc = mafDtc
            )
        )

        val misfireDtc = activeDtcs.any { it.startsWith("P030") }
        meshes.add(
            createSplineCable(
                id = "wire_ignition_loom",
                name = "Alimentación de Bobinas",
                points = listOf(
                    Vector3D(-38f, -35f, 2f),
                    Vector3D(-13f, -35f, 2f),
                    Vector3D(13f, -35f, 2f),
                    Vector3D(38f, -35f, 2f),
                    Vector3D(20f, -15f, -5f),
                    Vector3D(0f, 20f, -20f)
                ),
                radius = 2.2f,
                color = if (misfireDtc) Color(0xFFFF1744) else Color(0xFF880E4F),
                isActiveDtc = misfireDtc
            )
        )

        return meshes
    }

    /**
     * Builds a live semantic scene for any catalog system. A bounded page is rendered
     * for frame stability; the selected entity is always injected and centered.
     */
    fun buildUniversalCatalogScene(
        nodes: List<UniversalCatalogSceneNode>,
        selectedNodeId: String? = null,
        maxVisibleNodes: Int = 72
    ): List<Mesh3D> {
        if (nodes.isEmpty()) return emptyList()
        val selected = selectedNodeId?.let { id -> nodes.firstOrNull { it.id == id } }
        val visible = buildList {
            selected?.let(::add)
            nodes.asSequence()
                .filterNot { it.id == selected?.id }
                .take((maxVisibleNodes - size).coerceAtLeast(0))
                .forEach(::add)
        }.distinctBy { it.id }

        return visible.mapIndexed { index, node ->
            val seed = node.seed and 0xffffffffL
            val selectedNode = node.id == selectedNodeId
            val visualIndex = if (selectedNode) 0 else index
            val ring = (visualIndex / 12).coerceAtMost(5)
            val slot = visualIndex % 12
            val angle = ((2.0 * PI * slot) / 12.0 + ring * 0.22).toFloat()
            val radius = if (selectedNode) 0f else 30f + ring * 22f
            val position = Vector3D(
                x = cos(angle) * radius,
                y = if (selectedNode) -18f else -8f + ((seed shr 5) % 6).toFloat() * 5f,
                z = sin(angle) * radius
            )
            val base = catalogSystemColor(node.systemId)
            val color = if (selectedNode) Color(0xFFA3E635) else base
            val width = if (selectedNode) 27f else 9f + (seed % 9).toFloat()
            val height = if (selectedNode) 34f else 11f + ((seed shr 8) % 17).toFloat()
            val depth = if (selectedNode) 22f else 8f + ((seed shr 16) % 11).toFloat()
            val mesh = when ((seed % 3).toInt()) {
                0 -> createCylinder(
                    id = node.id,
                    name = node.name,
                    radius = width * 0.55f,
                    height = height,
                    segments = 8,
                    color = color,
                    position = position,
                    rotation = Vector3D(0f, angle * 0.4f, 0f),
                    isTranslucent = !selectedNode,
                    opacity = if (selectedNode) 1f else 0.82f
                )
                else -> createBox(
                    id = node.id,
                    name = node.name,
                    width = width,
                    height = height,
                    depth = depth,
                    color = color,
                    position = position,
                    rotation = Vector3D(angle * 0.08f, angle * 0.5f, angle * 0.04f),
                    isTranslucent = !selectedNode,
                    opacity = if (selectedNode) 1f else 0.84f
                )
            }
            mesh.copy(isHighlighted = selectedNode)
        }
    }

    private fun catalogSystemColor(systemId: String): Color = when (systemId) {
        "structure" -> Color(0xFF38BDF8)
        "engine" -> Color(0xFFF59E0B)
        "intake" -> Color(0xFF22D3EE)
        "forced_induction" -> Color(0xFFF97316)
        "transmission" -> Color(0xFF10B981)
        "suspension" -> Color(0xFFA3E635)
        "steering" -> Color(0xFF8B5CF6)
        "brakes" -> Color(0xFFFB7185)
        "wheels" -> Color(0xFFEAB308)
        "electrical" -> Color(0xFF60A5FA)
        "control_modules" -> Color(0xFFC084FC)
        "sensors" -> Color(0xFF2DD4BF)
        "actuators" -> Color(0xFFF472B6)
        "lighting" -> Color(0xFFFDE047)
        "passive_safety" -> Color(0xFFEF4444)
        "adas" -> Color(0xFF06B6D4)
        "body" -> Color(0xFF94A3B8)
        "wipers" -> Color(0xFF34D399)
        "infotainment" -> Color(0xFF818CF8)
        "access" -> Color(0xFFEC4899)
        "hybrid_ev" -> Color(0xFFFACC15)
        "hardware" -> Color(0xFFD1D5DB)
        else -> Color(0xFF0EA5E9)
    }

    val FRONT_SUSPENSION_NODE_IDS: Set<String> = linkedSetOf(
        "front_subframe", "subframe_bolts", "engine_mount_front",
        "front_left_wheel_bearing", "front_right_wheel_bearing",
        "front_left_lower_control_arm", "front_right_lower_control_arm",
        "front_left_arm_front_bushing", "front_left_arm_rear_bushing",
        "front_right_arm_front_bushing", "front_right_arm_rear_bushing",
        "front_left_ball_joint", "front_right_ball_joint",
        "front_left_strut", "front_right_strut", "front_left_spring", "front_right_spring",
        "front_left_strut_mount", "front_right_strut_mount",
        "front_left_strut_bearing", "front_right_strut_bearing",
        "front_left_bump_stop", "front_right_bump_stop",
        "front_left_dust_boot", "front_right_dust_boot",
        "stabilizer_bar", "left_stabilizer_link", "right_stabilizer_link",
        "stabilizer_bushing_left", "stabilizer_bushing_right",
        "front_left_knuckle", "front_right_knuckle",
        "front_left_wheel_hub", "front_right_wheel_hub",
        "wheel_nuts_front_left", "wheel_nuts_front_right",
        "steering_rack", "tie_rod_end_left", "tie_rod_end_right",
        "tie_rod_inner_left", "tie_rod_inner_right",
        "drive_shaft_left", "drive_shaft_right",
        "brake_disc_left", "brake_disc_right", "brake_caliper_left", "brake_caliper_right",
        "brake_pads_front", "front_left_abs_sensor", "front_right_abs_sensor"
    )

    /** Generic semantic front-suspension schematic. It is not an OEM or dimensional model. */
    fun buildFrontSuspensionScene(): List<Mesh3D> {
        val meshes = mutableListOf<Mesh3D>()
        val steel = Color(0xFF455A64)
        val cyan = Color(0xFF00B8D4)
        val rubber = Color(0xFF20262A)
        val brake = Color(0xFFD84315)

        meshes += createBox("front_subframe", "Bastidor auxiliar", 135f, 12f, 58f, steel, Vector3D(0f, 23f, 8f))
        meshes += createBox("steering_rack", "Cremallera de direccion", 118f, 9f, 11f, cyan, Vector3D(0f, 7f, -12f))
        meshes += createBox("front_left_lower_control_arm", "Tijereta izquierda", 62f, 8f, 17f, Color(0xFF78909C), Vector3D(-43f, 27f, 28f), rotation = Vector3D(0f, -0.28f, 0f))
        meshes += createBox("front_right_lower_control_arm", "Tijereta derecha", 62f, 8f, 17f, Color(0xFF78909C), Vector3D(43f, 27f, 28f), rotation = Vector3D(0f, 0.28f, 0f))
        meshes += createSplineCable("stabilizer_bar", "Barra estabilizadora", listOf(Vector3D(-82f, 13f, 4f), Vector3D(-48f, 18f, 17f), Vector3D(0f, 18f, 24f), Vector3D(48f, 18f, 17f), Vector3D(82f, 13f, 4f)), 3.5f, color = Color(0xFF00C853))

        listOf(-1f to "left", 1f to "right").forEach { (side, label) ->
            val x = side * 82f
            val prefix = if (label == "left") "front_left" else "front_right"
            meshes += createCylinder("${prefix}_strut", "Amortiguador $label", 7f, 68f, color = Color(0xFF546E7A), position = Vector3D(x, -7f, 15f))
            meshes += createCylinder("${prefix}_spring", "Resorte $label", 13f, 42f, color = Color(0xFF90A4AE), position = Vector3D(x, -9f, 15f), isTranslucent = true, opacity = 0.72f)
            meshes += createBox("${prefix}_knuckle", "Mangueta $label", 15f, 34f, 19f, steel, Vector3D(x, 18f, 18f))
            meshes += createCylinder("${prefix}_wheel_hub", "Cubo de rueda $label", 15f, 8f, color = cyan, position = Vector3D(x, 20f, 22f))
            meshes += createCylinder("${prefix}_wheel_bearing", "Rodamiento de rueda $label", 10f, 10f, color = Color(0xFFB0BEC5), position = Vector3D(x, 20f, 22f))
            meshes += createCylinder("${prefix}_ball_joint", "Rotula inferior $label", 5f, 12f, color = Color(0xFFFFB300), position = Vector3D(x, 29f, 25f))
            meshes += createCylinder("brake_disc_$label", "Disco de freno $label", 18f, 4f, color = Color(0xFFB0BEC5), position = Vector3D(x, 20f, 25f))
            meshes += createBox("brake_caliper_$label", "Mordaza de freno $label", 10f, 22f, 9f, brake, Vector3D(x + side * 17f, 19f, 25f))
            meshes += createCylinder("drive_shaft_$label", "Semieje $label", 4f, 62f, color = steel, position = Vector3D(side * 42f, 15f, 17f), rotation = Vector3D(0f, 0f, PI.toFloat() / 2f))
            meshes += createSplineCable("tie_rod_inner_$label", "Terminal interior $label", listOf(Vector3D(side * 18f, 7f, -12f), Vector3D(side * 55f, 13f, 5f)), 2.5f, color = cyan)
            meshes += createSplineCable("tie_rod_end_$label", "Terminal exterior $label", listOf(Vector3D(side * 55f, 13f, 5f), Vector3D(x, 17f, 16f)), 3f, color = Color(0xFF26C6DA))
            meshes += createCylinder("${label}_stabilizer_link", "Bieleta estabilizadora $label", 3f, 37f, color = Color(0xFF00C853), position = Vector3D(x, 5f, 7f))
        }

        val builtIds = meshes.mapTo(mutableSetOf()) { it.id }
        FRONT_SUSPENSION_NODE_IDS.filterNot(builtIds::contains).forEachIndexed { index, id ->
            val side = when {
                id.contains("left") -> -1f
                id.contains("right") -> 1f
                else -> 0f
            }
            val column = (index % 5) - 2
            val row = index / 5
            val x = if (side == 0f) column * 17f else side * (54f + (index % 4) * 8f)
            val y = 31f - row * 8f
            val z = 42f + (index % 3) * 9f
            val color = when {
                id.contains("bushing") || id.contains("dust") || id.contains("bump") -> rubber
                id.contains("brake") -> brake
                id.contains("abs_sensor") -> Color(0xFFFFC400)
                else -> Color(0xFF607D8B)
            }
            meshes += createBox(id, id.replace('_', ' '), 10f, 7f, 8f, color, Vector3D(x, y, z))
        }
        check(meshes.map { it.id }.toSet() == FRONT_SUSPENSION_NODE_IDS)
        return meshes
    }
}
