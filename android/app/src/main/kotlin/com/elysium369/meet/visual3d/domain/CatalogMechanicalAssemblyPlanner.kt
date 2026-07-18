package com.elysium369.meet.visual3d.domain

enum class MechanicalElementShape {
    CUBE,
    CYLINDER,
    SPHERE
}

enum class MechanicalMaterial {
    CAST_IRON,
    STEEL,
    ALUMINUM,
    COPPER,
    POLYMER
}

data class MechanicalElement(
    val shape: MechanicalElementShape,
    val material: MechanicalMaterial,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val sizeX: Float = 0f,
    val sizeY: Float = 0f,
    val sizeZ: Float = 0f,
    val radius: Float = 0f,
    val height: Float = 0f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f
)

data class CatalogServiceOffset(
    val x: Float,
    val y: Float,
    val z: Float
) {
    companion object {
        val ZERO = CatalogServiceOffset(0f, 0f, 0f)
    }
}

/**
 * Generic L2 mechanical atlas. Dimensions are visual proportions, never OEM measurements.
 */
object CatalogMechanicalAssemblyPlanner {
    private const val SERVICE_STAGE_COUNT = 6f

    fun elementsFor(name: String, primitive: SemanticPrimitive): List<MechanicalElement> {
        val normalized = name.lowercase()
        return when {
            "bloque de motor" in normalized -> engineBlock()
            normalized == "cigüeñal" || normalized == "ciguenal" -> crankshaft()
            normalized.startsWith("pist") -> pistonAndRod()
            normalized.startsWith("biela") -> connectingRod()
            primitive == SemanticPrimitive.DISC -> discAssembly()
            primitive == SemanticPrimitive.CHAIN -> chainDrive()
            primitive == SemanticPrimitive.SHAFT -> shaftAssembly()
            primitive == SemanticPrimitive.BOX -> housingAssembly()
            primitive == SemanticPrimitive.PISTON -> pistonAndRod()
            else -> cylindricalComponent()
        }
    }

    /** A compact selectable token for a repeated source record, not another claimed physical part. */
    fun sourceRecordToken(occurrence: Int): List<MechanicalElement> {
        require(occurrence > 0) { "source record token requires a duplicate occurrence" }
        return listOf(
            cube(
                material = MechanicalMaterial.POLYMER,
                sizeX = 0.030f,
                sizeY = 0.010f,
                sizeZ = 0.018f
            ),
            cylinder(
                material = MechanicalMaterial.COPPER,
                radius = 0.004f,
                height = 0.014f,
                x = -0.009f,
                z = 0.011f
            ),
            cylinder(
                material = MechanicalMaterial.STEEL,
                radius = 0.004f,
                height = 0.014f,
                x = 0.009f,
                z = 0.011f
            )
        )
    }

    fun serviceOrder(name: String, primitive: SemanticPrimitive): Int {
        val normalized = name.lowercase()
        return when {
            primitive == SemanticPrimitive.CHAIN || "tapa" in normalized -> 1
            primitive == SemanticPrimitive.DISC || "sensor" in normalized || "tapón" in normalized -> 2
            normalized.startsWith("pist") || normalized.startsWith("biela") -> 3
            "cojinete" in normalized || "retén" in normalized || "reten" in normalized -> 4
            primitive == SemanticPrimitive.SHAFT -> 5
            else -> 6
        }
    }

    fun serviceOffset(
        name: String,
        primitive: SemanticPrimitive,
        assembledX: Float,
        progress: Float
    ): CatalogServiceOffset {
        val order = serviceOrder(name, primitive)
        val stageStart = (order - 1f) / SERVICE_STAGE_COUNT
        val localProgress = ((progress.coerceIn(0f, 1f) - stageStart) * SERVICE_STAGE_COUNT)
            .coerceIn(0f, 1f)
        if (localProgress == 0f) return CatalogServiceOffset.ZERO

        val direction = if (assembledX < 0f) -1f else 1f
        val target = when (order) {
            1 -> CatalogServiceOffset(direction * 0.58f, 0.05f, 0.78f)
            2 -> CatalogServiceOffset(direction * 0.82f, 0.02f, 0.34f)
            3 -> CatalogServiceOffset(direction * 0.30f, 0.03f, 0.94f)
            4 -> CatalogServiceOffset(direction * 0.38f, -0.08f, -0.62f)
            5 -> CatalogServiceOffset(direction * 0.32f, -0.10f, -0.92f)
            else -> CatalogServiceOffset(0f, 0.12f, -0.44f)
        }
        val eased = localProgress * localProgress * (3f - 2f * localProgress)
        return CatalogServiceOffset(target.x * eased, target.y * eased, target.z * eased)
    }

    private fun engineBlock(): List<MechanicalElement> = buildList {
        add(cube(MechanicalMaterial.CAST_IRON, sizeX = 0.060f, sizeY = 0.056f, sizeZ = 0.038f, z = -0.018f))
        add(cube(MechanicalMaterial.CAST_IRON, sizeX = 0.064f, sizeY = 0.014f, sizeZ = 0.052f, y = -0.035f, z = 0.010f))
        add(cube(MechanicalMaterial.CAST_IRON, sizeX = 0.064f, sizeY = 0.014f, sizeZ = 0.052f, y = 0.035f, z = 0.010f))
        listOf(-0.036f, -0.012f, 0.012f, 0.036f).forEach { x ->
            add(cylinder(MechanicalMaterial.STEEL, radius = 0.0105f, height = 0.050f, x = x, z = 0.028f))
        }
    }

    private fun crankshaft(): List<MechanicalElement> = buildList {
        add(cylinder(MechanicalMaterial.STEEL, radius = 0.0075f, height = 0.150f, rotationZ = 90f))
        listOf(-0.045f, -0.015f, 0.015f, 0.045f).forEachIndexed { index, x ->
            val throwZ = if (index == 0 || index == 3) 0.014f else -0.014f
            add(cylinder(MechanicalMaterial.STEEL, radius = 0.010f, height = 0.025f, x = x, z = throwZ, rotationZ = 90f))
            add(cylinder(MechanicalMaterial.CAST_IRON, radius = 0.019f, height = 0.008f, x = x - 0.008f, z = -throwZ, rotationZ = 90f))
        }
    }

    private fun pistonAndRod(): List<MechanicalElement> = listOf(
        cylinder(MechanicalMaterial.ALUMINUM, radius = 0.025f, height = 0.035f, z = 0.018f),
        cylinder(MechanicalMaterial.STEEL, radius = 0.006f, height = 0.060f, z = 0.010f, rotationZ = 90f),
        cube(MechanicalMaterial.STEEL, sizeX = 0.012f, sizeY = 0.012f, sizeZ = 0.060f, z = -0.032f),
        cylinder(MechanicalMaterial.STEEL, radius = 0.014f, height = 0.014f, z = -0.065f)
    )

    private fun connectingRod(): List<MechanicalElement> = listOf(
        cylinder(MechanicalMaterial.STEEL, radius = 0.010f, height = 0.012f, z = 0.035f),
        cube(MechanicalMaterial.STEEL, sizeX = 0.012f, sizeY = 0.012f, sizeZ = 0.065f),
        cylinder(MechanicalMaterial.STEEL, radius = 0.016f, height = 0.014f, z = -0.038f)
    )

    private fun discAssembly(): List<MechanicalElement> = listOf(
        cylinder(MechanicalMaterial.CAST_IRON, radius = 0.048f, height = 0.018f, rotationX = 90f),
        cylinder(MechanicalMaterial.STEEL, radius = 0.019f, height = 0.028f, rotationX = 90f),
        cylinder(MechanicalMaterial.COPPER, radius = 0.007f, height = 0.034f, rotationX = 90f)
    )

    private fun chainDrive(): List<MechanicalElement> = listOf(
        cylinder(MechanicalMaterial.STEEL, radius = 0.026f, height = 0.014f, z = 0.042f, rotationX = 90f),
        cylinder(MechanicalMaterial.STEEL, radius = 0.020f, height = 0.014f, z = -0.042f, rotationX = 90f),
        cylinder(MechanicalMaterial.COPPER, radius = 0.006f, height = 0.078f, x = -0.026f),
        cylinder(MechanicalMaterial.COPPER, radius = 0.006f, height = 0.078f, x = 0.026f)
    )

    private fun shaftAssembly(): List<MechanicalElement> = listOf(
        cylinder(MechanicalMaterial.STEEL, radius = 0.010f, height = 0.145f, rotationZ = 90f),
        cylinder(MechanicalMaterial.CAST_IRON, radius = 0.020f, height = 0.014f, x = -0.052f, rotationZ = 90f),
        cylinder(MechanicalMaterial.CAST_IRON, radius = 0.020f, height = 0.014f, x = 0.052f, rotationZ = 90f)
    )

    private fun housingAssembly(): List<MechanicalElement> = listOf(
        cube(MechanicalMaterial.CAST_IRON, sizeX = 0.060f, sizeY = 0.070f, sizeZ = 0.050f),
        cube(MechanicalMaterial.ALUMINUM, sizeX = 0.046f, sizeY = 0.076f, sizeZ = 0.012f, z = 0.031f),
        cylinder(MechanicalMaterial.STEEL, radius = 0.008f, height = 0.080f, rotationZ = 90f)
    )

    private fun cylindricalComponent(): List<MechanicalElement> = listOf(
        cylinder(MechanicalMaterial.ALUMINUM, radius = 0.026f, height = 0.070f),
        cylinder(MechanicalMaterial.STEEL, radius = 0.011f, height = 0.092f),
        cube(MechanicalMaterial.POLYMER, sizeX = 0.024f, sizeY = 0.022f, sizeZ = 0.026f, x = 0.026f, z = 0.018f)
    )

    private fun cube(
        material: MechanicalMaterial,
        sizeX: Float,
        sizeY: Float,
        sizeZ: Float,
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f
    ) = MechanicalElement(
        shape = MechanicalElementShape.CUBE,
        material = material,
        x = x,
        y = y,
        z = z,
        sizeX = sizeX,
        sizeY = sizeY,
        sizeZ = sizeZ
    )

    private fun cylinder(
        material: MechanicalMaterial,
        radius: Float,
        height: Float,
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f,
        rotationX: Float = 0f,
        rotationY: Float = 0f,
        rotationZ: Float = 0f
    ) = MechanicalElement(
        shape = MechanicalElementShape.CYLINDER,
        material = material,
        x = x,
        y = y,
        z = z,
        radius = radius,
        height = height,
        rotationX = rotationX,
        rotationY = rotationY,
        rotationZ = rotationZ
    )
}
