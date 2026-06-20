package com.elysium369.meet.core.engine3d

import androidx.compose.ui.graphics.Color
import kotlin.math.*

enum class EngineType(val label: String) {
    INLINE_4("4 Cilindros en Línea (L4)"),
    V6("Motor en V6 (V6)"),
    V8("Motor en V8 (V8)"),
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

    fun buildRelayFuseBoxScene(engineType: EngineType, activeDtcs: List<String>): List<Mesh3D> {
        val meshes = mutableListOf<Mesh3D>()

        meshes.add(
            createBox(
                id = "fuse_box_housing",
                name = "Carcasa Caja de Fusibles",
                width = 130f,
                height = 30f,
                depth = 95f,
                color = Color(0xFF121418),
                position = Vector3D(0f, 0f, 0f)
            )
        )
        meshes.add(
            createBox(
                id = "fuse_box_tray",
                name = "Bandeja de Fusibles",
                width = 122f,
                height = 4f,
                depth = 87f,
                color = Color(0xFF2A2E35),
                position = Vector3D(0f, -14f, 0f)
            )
        )

        if (engineType == EngineType.ELECTRIC) {
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
            Triple("relay_fuel_pump", "Relé Bomba Gasolina", Vector3D(-40f, -21f, -25f)),
            Triple("relay_starter", "Relé del Motor de Arranque", Vector3D(-10f, -21f, -25f)),
            Triple("relay_ignition", "Relé Principal de Ignición", Vector3D(20f, -21f, -25f)),
            Triple("relay_fan", "Relé del Ventilador Radiador", Vector3D(50f, -21f, -25f)),
            Triple("relay_horn", "Relé de Bocina", Vector3D(-40f, -21f, 25f)),
            Triple("relay_lights", "Relé de Faros Principales", Vector3D(20f, -21f, 25f))
        )

        relayGrid.forEach { (id, name, pos) ->
            val isFailing = when (id) {
                "relay_fuel_pump" -> activeDtcs.contains("P0230") || activeDtcs.contains("P0087")
                "relay_starter" -> activeDtcs.contains("P0512")
                "relay_fan" -> activeDtcs.contains("P0480")
                else -> false
            }

            meshes.add(
                createBox(
                    id = id,
                    name = name,
                    width = 22f,
                    height = 15f,
                    depth = 22f,
                    color = Color(0xFF1E2228),
                    position = pos,
                    isActiveDtc = isFailing
                )
            )
        }

        val fusePositions = listOf(
            Pair(Vector3D(-45f, -18f, -2f), Color(0xFFFF1744)),
            Pair(Vector3D(-32f, -18f, -2f), Color(0xFF2979FF)),
            Pair(Vector3D(-19f, -18f, -2f), Color(0xFFFFEA00)),
            Pair(Vector3D(-6f, -18f, -2f), Color(0xFF00E676)),
            Pair(Vector3D(7f, -18f, -2f), Color(0xFFFF1744)),
            Pair(Vector3D(20f, -18f, -2f), Color(0xFF2979FF)),
            Pair(Vector3D(33f, -18f, -2f), Color(0xFFFFEA00)),
            Pair(Vector3D(46f, -18f, -2f), Color(0xFF00E676)),
            Pair(Vector3D(-45f, -18f, 10f), Color(0xFFFF9100)),
            Pair(Vector3D(-32f, -18f, 10f), Color(0xFF2979FF)),
            Pair(Vector3D(-19f, -18f, 10f), Color(0xFFFF1744)),
            Pair(Vector3D(-6f, -18f, 10f), Color(0xFFFFEA00)),
            Pair(Vector3D(7f, -18f, 10f), Color(0xFF00E676))
        )

        fusePositions.forEachIndexed { index, (pos, fuseColor) ->
            val isBlown = when (index) {
                0 -> activeDtcs.contains("P0201") || activeDtcs.contains("P0202")
                4 -> activeDtcs.contains("P0115")
                5 -> activeDtcs.contains("P0351") || activeDtcs.contains("P0352")
                10 -> activeDtcs.contains("P0230")
                else -> false
            }

            meshes.add(
                createBox(
                    id = "fuse_$index",
                    name = "Fusible F$index",
                    width = 8f,
                    height = 9f,
                    depth = 5f,
                    color = fuseColor,
                    position = pos,
                    isTranslucent = true,
                    opacity = 0.7f,
                    isActiveDtc = isBlown
                )
            )

            val metalColor = if (isBlown) Color(0xFF3E2723) else Color(0xFFCFD8DC)
            meshes.add(
                Mesh3D(
                    id = "fuse_element_$index",
                    name = "Elemento Fusible F$index",
                    vertices = if (isBlown) {
                        listOf(
                            pos + Vector3D(-2f, 1f, 0f),
                            pos + Vector3D(-1f, 3f, 0f),
                            pos + Vector3D(1f, 3f, 0f),
                            pos + Vector3D(2f, 1f, 0f)
                        )
                    } else {
                        listOf(
                            pos + Vector3D(-2f, 1f, 0f),
                            pos + Vector3D(0f, 3f, 0f),
                            pos + Vector3D(2f, 1f, 0f)
                        )
                    },
                    faces = if (isBlown) {
                        listOf(
                            Face3D(listOf(0, 1), metalColor, isTranslucent = true, isLineOnly = true),
                            Face3D(listOf(2, 3), metalColor, isTranslucent = true, isLineOnly = true)
                        )
                    } else {
                        listOf(
                            Face3D(listOf(0, 1, 2), metalColor, isTranslucent = true, isLineOnly = true)
                        )
                    }
                )
            )
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
}
