package com.elysium.vanguard.forge.engine

import com.elysium.vanguard.forge.domain.BoundingBox
import com.elysium.vanguard.forge.domain.CollisionShape
import com.elysium.vanguard.forge.domain.CompiledFace
import com.elysium.vanguard.forge.domain.CompiledMesh
import com.elysium.vanguard.forge.domain.CompiledVertex
import com.elysium.vanguard.forge.domain.FeatureOperation
import com.elysium.vanguard.forge.domain.FeatureType
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.GeometryCompileResult
import com.elysium.vanguard.forge.domain.GeometryValidationResult
import com.elysium.vanguard.forge.domain.ParametricFeature
import com.elysium.vanguard.forge.domain.Vector3Data
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class ForgeGeometryCompiler {
    fun compilePart(part: ForgePart): GeometryCompileResult {
        val cacheKey = part.renderCacheKey
            ?: "${part.artifact.id}_${part.artifact.version}_${part.featureTree.hashCode()}_${part.dimensions.hashCode()}"
        val validation = validateGeometry(part)
        if (!validation.isValid) {
            return placeholder(cacheKey, validation.errors, validation.warnings)
        }

        val mesh = MeshAccumulator()
        val warnings = validation.warnings.toMutableList()
        val additiveFeatures = part.featureTree.filter { it.operation != FeatureOperation.SUBTRACT }

        additiveFeatures.forEach { feature ->
            when (feature.type) {
                FeatureType.BOX -> mesh.merge(box(feature, part))
                FeatureType.PLATE -> mesh.merge(box(feature, part, forcePlate = true))
                FeatureType.CYLINDER, FeatureType.CONE, FeatureType.SPHERE -> mesh.merge(cylinder(feature, part, hollow = false))
                FeatureType.TUBE -> mesh.merge(cylinder(feature, part, hollow = true))
                FeatureType.PROFILE_U -> mesh.merge(profileU(feature, part))
                FeatureType.PROFILE_L -> mesh.merge(profileL(feature, part))
                FeatureType.CIRCULAR_PATTERN -> mesh.merge(circularHoleMarkers(feature, part))
                FeatureType.HOLE -> mesh.merge(singleHoleMarker(feature, part))
                else -> {
                    warnings += "${feature.type} se conserva parametricamente, pero V1 la muestra como marcador."
                    mesh.merge(marker(feature, part))
                }
            }
        }

        part.featureTree
            .filter { it.operation == FeatureOperation.SUBTRACT && it.type == FeatureType.HOLE }
            .forEach {
                warnings += "HOLE ${it.id} es un feature editable; V1 lo renderiza como perforacion visual sin boolean CAD completo."
                mesh.merge(singleHoleMarker(it, part))
            }

        if (mesh.vertices.isEmpty() || mesh.faces.isEmpty()) {
            mesh.merge(box(defaultBody(part), part))
            warnings += "Pieza sin feature renderizable; se genero cuerpo base desde dimensiones."
        }

        val compiled = mesh.toMesh()
        val bounds = boundsFor(compiled.vertices)
        return GeometryCompileResult(
            mesh = compiled.copy(min = bounds.min, max = bounds.max),
            boundingBox = bounds,
            collisionShape = CollisionShape.BoxShape(bounds.size * 0.5),
            cacheKey = cacheKey,
            usedFallback = false,
            warnings = warnings.distinct()
        )
    }

    fun validateGeometry(part: ForgePart): GeometryValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // (1) DimensionSet field-level checks.
        val dimFieldChecks: List<Pair<String, Double?>> = listOf(
            "lengthMm" to part.dimensions.lengthMm,
            "widthMm" to part.dimensions.widthMm,
            "heightMm" to part.dimensions.heightMm,
            "diameterMm" to part.dimensions.diameterMm,
            "innerDiameterMm" to part.dimensions.innerDiameterMm,
            "outerDiameterMm" to part.dimensions.outerDiameterMm,
            "thicknessMm" to part.dimensions.thicknessMm,
            "toleranceMm" to part.dimensions.toleranceMm
        )
        val dimensionalKeys = setOf(
            "lengthMm", "widthMm", "heightMm", "diameterMm",
            "innerDiameterMm", "outerDiameterMm", "thicknessMm"
        )
        dimFieldChecks.forEach { (name, value) ->
            if (value == null) return@forEach
            if (!value.isFinite()) {
                errors += "dimensions.$name no es finito."
            } else if (value < 0.0) {
                errors += "dimensions.$name no puede ser negativo."
            } else if (value == 0.0 && name in dimensionalKeys) {
                warnings += "dimensions.$name es 0.0; se usara valor por defecto."
            }
        }
        part.dimensions.customDimensions.forEach { (k, v) ->
            if (!v.isFinite()) errors += "dimensions.customDimensions.$k no es finito."
            else if (v < 0.0) errors += "dimensions.customDimensions.$k no puede ser negativo."
        }

        val hasDimensions = listOf(
            part.dimensions.lengthMm,
            part.dimensions.widthMm,
            part.dimensions.heightMm,
            part.dimensions.diameterMm,
            part.dimensions.outerDiameterMm,
            part.dimensions.thicknessMm
        ).any { it != null && it > 0.0 }

        if (!hasDimensions && part.featureTree.isEmpty()) {
            errors += "La pieza necesita dimensiones o features parametricos."
        }
        if (part.featureTree.size > 96) {
            warnings += "Feature tree alto para movil; puede afectar render."
        }
        part.featureTree.forEach { feature ->
            feature.parameters.forEach { (name, value) ->
                if (!value.isFinite()) {
                    errors += "${feature.id}.$name no es finito."
                } else if (value < 0.0 && (name.contains("diameter", true) ||
                            name.contains("thickness", true) || name.contains("length", true))) {
                    errors += "${feature.id}.$name no puede ser negativo."
                } else if (value == 0.0 && (name.contains("diameter", true) ||
                            name.contains("thickness", true) || name.contains("length", true))) {
                    warnings += "${feature.id}.$name es 0.0; se usara valor por defecto."
                }
            }
            if (!feature.type.supportedV1) {
                warnings += "${feature.type} permanece editable; renderer V1 lo simplifica."
            }
        }
        return GeometryValidationResult(errors.isEmpty(), errors, warnings)
    }

    private fun defaultBody(part: ForgePart): ParametricFeature =
        ParametricFeature(
            id = "default_body",
            type = if ((part.dimensions.diameterMm ?: part.dimensions.outerDiameterMm) != null) FeatureType.CYLINDER else FeatureType.BOX,
            parameters = mapOf(
                "lengthMm" to (part.dimensions.lengthMm ?: 120.0),
                "widthMm" to (part.dimensions.widthMm ?: part.dimensions.outerDiameterMm ?: part.dimensions.diameterMm ?: 80.0),
                "heightMm" to (part.dimensions.heightMm ?: part.dimensions.thicknessMm ?: 20.0),
                "diameterMm" to (part.dimensions.diameterMm ?: part.dimensions.outerDiameterMm ?: 80.0)
            )
        )

    private fun box(feature: ParametricFeature, part: ForgePart, forcePlate: Boolean = false): MeshAccumulator {
        val length = feature.dim("lengthMm", "length", "l") ?: part.dimensions.lengthMm ?: 120.0
        val width = feature.dim("widthMm", "width", "w") ?: part.dimensions.widthMm ?: 80.0
        val height = if (forcePlate) {
            feature.dim("thicknessMm", "thickness", "heightMm", "height") ?: part.dimensions.thicknessMm ?: 4.0
        } else {
            feature.dim("heightMm", "height", "h", "thicknessMm", "thickness") ?: part.dimensions.heightMm ?: part.dimensions.thicknessMm ?: 20.0
        }
        return boxAt(feature, length, width, height, 0.08f, 0.88f, 0.9f)
    }

    private fun profileU(feature: ParametricFeature, part: ForgePart): MeshAccumulator {
        val length = feature.dim("lengthMm", "length", "l") ?: part.dimensions.lengthMm ?: 260.0
        val width = feature.dim("widthMm", "width", "w") ?: part.dimensions.widthMm ?: 56.0
        val height = feature.dim("heightMm", "height", "h") ?: part.dimensions.heightMm ?: 36.0
        val thickness = feature.dim("thicknessMm", "thickness", "t") ?: part.dimensions.thicknessMm ?: 4.0
        val result = MeshAccumulator()
        val baseZ = -height / 2.0 + thickness / 2.0
        val flangeZ = -height / 2.0 + height / 2.0
        result.merge(boxAt(feature, length, width, thickness, 0.03f, 0.72f, 0.72f, localOffset = Vector3Data(0.0, 0.0, baseZ)))
        result.merge(boxAt(feature, length, thickness, height, 0.02f, 0.82f, 0.82f, localOffset = Vector3Data(0.0, width / 2.0 - thickness / 2.0, flangeZ)))
        result.merge(boxAt(feature, length, thickness, height, 0.02f, 0.82f, 0.82f, localOffset = Vector3Data(0.0, -width / 2.0 + thickness / 2.0, flangeZ)))
        return result
    }

    private fun profileL(feature: ParametricFeature, part: ForgePart): MeshAccumulator {
        val length = feature.dim("lengthMm", "length", "l") ?: part.dimensions.lengthMm ?: 180.0
        val width = feature.dim("widthMm", "width", "w") ?: part.dimensions.widthMm ?: 44.0
        val height = feature.dim("heightMm", "height", "h") ?: part.dimensions.heightMm ?: 36.0
        val thickness = feature.dim("thicknessMm", "thickness", "t") ?: part.dimensions.thicknessMm ?: 4.0
        val result = MeshAccumulator()
        result.merge(boxAt(feature, length, width, thickness, 0.08f, 0.78f, 0.82f, localOffset = Vector3Data(0.0, 0.0, -height / 2.0 + thickness / 2.0)))
        result.merge(boxAt(feature, length, thickness, height, 0.08f, 0.78f, 0.82f, localOffset = Vector3Data(0.0, -width / 2.0 + thickness / 2.0, 0.0)))
        return result
    }

    private fun cylinder(feature: ParametricFeature, part: ForgePart, hollow: Boolean): MeshAccumulator {
        val outerDiameter = feature.dim("outerDiameterMm", "outerDiameter", "diameterMm", "diameter", "outer", "d")
            ?: part.dimensions.outerDiameterMm
            ?: part.dimensions.diameterMm
            ?: 80.0
        val innerDiameter = feature.dim("innerDiameterMm", "innerDiameter", "inner")
            ?: part.dimensions.innerDiameterMm
            ?: if (hollow) outerDiameter * 0.45 else 0.0
        val height = feature.dim("heightMm", "height", "thicknessMm", "thickness", "h")
            ?: part.dimensions.heightMm
            ?: part.dimensions.thicknessMm
            ?: 24.0
        return ringCylinder(feature, outerDiameter, if (hollow) innerDiameter else 0.0, height, 0.62f, 0.66f, 0.68f)
    }

    private fun circularHoleMarkers(feature: ParametricFeature, part: ForgePart): MeshAccumulator {
        val count = (feature.dim("count", "holes") ?: 5.0).toInt().coerceIn(1, 24)
        val boltCircleDiameter = feature.dim("boltCircleDiameterMm", "boltCircleDiameter", "bcd") ?: 114.3
        val holeDiameter = feature.dim("holeDiameterMm", "holeDiameter", "diameterMm", "diameter") ?: 12.5
        val topZ = (part.dimensions.thicknessMm ?: part.dimensions.heightMm ?: 20.0) / 2.0 + 0.6
        val angleOffset = feature.dim("angleOffsetDeg", "angleOffset") ?: 0.0
        val result = MeshAccumulator()
        repeat(count) { index ->
            val angle = Math.toRadians(angleOffset + index * 360.0 / count)
            val x = cos(angle) * boltCircleDiameter / 2.0
            val y = sin(angle) * boltCircleDiameter / 2.0
            val marker = feature.copy(position = feature.position + Vector3Data(x, y, topZ))
            result.merge(flatDisc(marker, holeDiameter, 0.02f, 0.02f, 0.025f))
        }
        return result
    }

    private fun singleHoleMarker(feature: ParametricFeature, part: ForgePart): MeshAccumulator {
        val holeDiameter = feature.dim("holeDiameterMm", "holeDiameter", "diameterMm", "diameter", "d") ?: 12.0
        val topZ = feature.position.z.takeIf { it != 0.0 } ?: ((part.dimensions.thicknessMm ?: part.dimensions.heightMm ?: 20.0) / 2.0 + 0.8)
        return flatDisc(feature.copy(position = feature.position.copy(z = topZ)), holeDiameter, 0.02f, 0.02f, 0.025f)
    }

    private fun marker(feature: ParametricFeature, part: ForgePart): MeshAccumulator =
        boxAt(feature, part.dimensions.lengthMm ?: 36.0, part.dimensions.widthMm ?: 18.0, part.dimensions.heightMm ?: 6.0, 0.9f, 0.52f, 0.04f)

    private fun boxAt(
        feature: ParametricFeature,
        length: Double,
        width: Double,
        height: Double,
        r: Float,
        g: Float,
        b: Float,
        localOffset: Vector3Data = Vector3Data.ZERO
    ): MeshAccumulator {
        val lx = length.safePositive() / 2.0
        val wy = width.safePositive() / 2.0
        val hz = height.safePositive() / 2.0
        val local = listOf(
            Vector3Data(-lx, -wy, -hz), Vector3Data(lx, -wy, -hz), Vector3Data(lx, wy, -hz), Vector3Data(-lx, wy, -hz),
            Vector3Data(-lx, -wy, hz), Vector3Data(lx, -wy, hz), Vector3Data(lx, wy, hz), Vector3Data(-lx, wy, hz)
        ).map { transform(feature, it + localOffset) }
        val result = MeshAccumulator()
        val ids = local.map { result.add(it, r, g, b) }
        result.addFaces(
            ids[0], ids[1], ids[2], ids[0], ids[2], ids[3],
            ids[4], ids[6], ids[5], ids[4], ids[7], ids[6],
            ids[0], ids[4], ids[5], ids[0], ids[5], ids[1],
            ids[1], ids[5], ids[6], ids[1], ids[6], ids[2],
            ids[2], ids[6], ids[7], ids[2], ids[7], ids[3],
            ids[3], ids[7], ids[4], ids[3], ids[4], ids[0]
        )
        return result
    }

    private fun ringCylinder(
        feature: ParametricFeature,
        outerDiameter: Double,
        innerDiameter: Double,
        height: Double,
        r: Float,
        g: Float,
        b: Float
    ): MeshAccumulator {
        val outer = (outerDiameter.safePositive() / 2.0).coerceAtLeast(1.0)
        val inner = (innerDiameter / 2.0).coerceIn(0.0, outer * 0.92)
        val h = height.safePositive() / 2.0
        val segments = 48
        val result = MeshAccumulator()
        val outerBottom = mutableListOf<Int>()
        val outerTop = mutableListOf<Int>()
        val innerBottom = mutableListOf<Int>()
        val innerTop = mutableListOf<Int>()
        repeat(segments) { i ->
            val angle = i / segments.toDouble() * PI * 2.0
            val c = cos(angle)
            val s = sin(angle)
            outerBottom += result.add(transform(feature, Vector3Data(c * outer, s * outer, -h)), r, g, b)
            outerTop += result.add(transform(feature, Vector3Data(c * outer, s * outer, h)), r, g, b)
            if (inner > 0.0) {
                innerBottom += result.add(transform(feature, Vector3Data(c * inner, s * inner, -h)), 0.03f, 0.05f, 0.06f)
                innerTop += result.add(transform(feature, Vector3Data(c * inner, s * inner, h)), 0.03f, 0.05f, 0.06f)
            }
        }
        repeat(segments) { i ->
            val n = (i + 1) % segments
            result.addFaces(outerBottom[i], outerBottom[n], outerTop[n], outerBottom[i], outerTop[n], outerTop[i])
            if (inner > 0.0) {
                result.addFaces(innerBottom[n], innerBottom[i], innerTop[i], innerBottom[n], innerTop[i], innerTop[n])
                result.addFaces(outerTop[i], outerTop[n], innerTop[n], outerTop[i], innerTop[n], innerTop[i])
                result.addFaces(outerBottom[n], outerBottom[i], innerBottom[i], outerBottom[n], innerBottom[i], innerBottom[n])
            } else {
                val bottomCenter = result.add(transform(feature, Vector3Data(0.0, 0.0, -h)), r, g, b)
                val topCenter = result.add(transform(feature, Vector3Data(0.0, 0.0, h)), r, g, b)
                result.addFaces(bottomCenter, outerBottom[n], outerBottom[i])
                result.addFaces(topCenter, outerTop[i], outerTop[n])
            }
        }
        return result
    }

    private fun flatDisc(feature: ParametricFeature, diameter: Double, r: Float, g: Float, b: Float): MeshAccumulator {
        val radius = diameter.safePositive() / 2.0
        val segments = 24
        val result = MeshAccumulator()
        val center = result.add(transform(feature, Vector3Data.ZERO), r, g, b)
        val ring = List(segments) { i ->
            val angle = i / segments.toDouble() * PI * 2.0
            result.add(transform(feature, Vector3Data(cos(angle) * radius, sin(angle) * radius, 0.0)), r, g, b)
        }
        repeat(segments) { i ->
            result.addFaces(center, ring[i], ring[(i + 1) % segments])
        }
        return result
    }

    private fun transform(feature: ParametricFeature, local: Vector3Data): Vector3Data {
        val angle = Math.toRadians(feature.rotation.z)
        val cx = cos(angle)
        val sx = sin(angle)
        val rotated = Vector3Data(
            x = local.x * cx - local.y * sx,
            y = local.x * sx + local.y * cx,
            z = local.z
        )
        return rotated + feature.position
    }

    private fun boundsFor(vertices: List<CompiledVertex>): BoundingBox {
        if (vertices.isEmpty()) return BoundingBox(Vector3Data.ZERO, Vector3Data.ZERO)
        var minX = vertices.first().x.toDouble()
        var minY = vertices.first().y.toDouble()
        var minZ = vertices.first().z.toDouble()
        var maxX = minX
        var maxY = minY
        var maxZ = minZ
        vertices.forEach {
            minX = min(minX, it.x.toDouble())
            minY = min(minY, it.y.toDouble())
            minZ = min(minZ, it.z.toDouble())
            maxX = max(maxX, it.x.toDouble())
            maxY = max(maxY, it.y.toDouble())
            maxZ = max(maxZ, it.z.toDouble())
        }
        return BoundingBox(Vector3Data(minX, minY, minZ), Vector3Data(maxX, maxY, maxZ))
    }

    private fun placeholder(cacheKey: String, errors: List<String>, warnings: List<String>): GeometryCompileResult {
        val fallback = boxAt(defaultBodyForPlaceholder(), 80.0, 50.0, 20.0, 0.9f, 0.15f, 0.18f).toMesh()
        val bounds = boundsFor(fallback.vertices)
        return GeometryCompileResult(
            mesh = fallback.copy(min = bounds.min, max = bounds.max),
            boundingBox = bounds,
            collisionShape = CollisionShape.BoxShape(bounds.size * 0.5),
            cacheKey = cacheKey,
            usedFallback = true,
            warnings = warnings,
            errors = errors
        )
    }

    private fun defaultBodyForPlaceholder(): ParametricFeature =
        ParametricFeature(id = "fallback_body", type = FeatureType.BOX)

    private fun ParametricFeature.dim(vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { parameters[it] }

    private fun Double.safePositive(): Double =
        takeIf { it.isFinite() && it > 0.0 } ?: 1.0

    private class MeshAccumulator {
        val vertices = mutableListOf<CompiledVertex>()
        val faces = mutableListOf<CompiledFace>()

        fun add(position: Vector3Data, r: Float, g: Float, b: Float): Int {
            vertices += CompiledVertex(
                x = position.x.toFloat(),
                y = position.y.toFloat(),
                z = position.z.toFloat(),
                r = r,
                g = g,
                b = b
            )
            return vertices.lastIndex
        }

        fun addFaces(vararg indices: Int) {
            indices.asList().chunked(3).forEach { tri ->
                if (tri.size == 3) faces += CompiledFace(tri[0], tri[1], tri[2])
            }
        }

        fun merge(other: MeshAccumulator) {
            val offset = vertices.size
            vertices += other.vertices
            faces += other.faces.map { CompiledFace(it.a + offset, it.b + offset, it.c + offset) }
        }

        fun toMesh(): CompiledMesh = CompiledMesh(vertices = vertices, faces = faces)
    }
}
