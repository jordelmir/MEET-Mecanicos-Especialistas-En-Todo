package com.elysium.vanguard.forge.domain

import kotlinx.serialization.Serializable

/**
 * Tipos de features paramétricos que ForgeGeometryCompiler reconoce V1.
 * V1 soporta solo los marcados como soportados; el resto cae a MARK visual.
 */
@Serializable
enum class FeatureType(val supportedV1: Boolean) {
    BOX(true),
    CYLINDER(true),
    TUBE(true),
    PLATE(true),
    SPHERE(true),
    CONE(true),
    PROFILE_L(true),
    PROFILE_U(true),
    EXTRUSION(false),
    REVOLVE(false),
    HOLE(true),
    CUT(false),
    CHAMFER(false),
    FILLET(false),
    SLOT(false),
    THREAD_VISUAL(false),
    LINEAR_PATTERN(false),
    CIRCULAR_PATTERN(true),
    MIRROR(false);

    val isPrimitive: Boolean
        get() = this == BOX || this == CYLINDER || this == TUBE ||
                this == PLATE || this == SPHERE || this == CONE
}

@Serializable
enum class FeatureOperation { ADD, SUBTRACT, MODIFY, PATTERN }

/**
 * Feature paramétrico de una pieza. V1: las primitivas y agujeros reales.
 * Resto (EXTRUSION, REVOLVE, ...) caen a MARK visual con metadatos.
 */
@Serializable
data class ParametricFeature(
    val id: String,
    val type: FeatureType,
    val name: String = "",
    val parameters: Map<String, Double> = emptyMap(),
    val position: Vector3Data = Vector3Data.ZERO,
    val rotation: Vector3Data = Vector3Data.ZERO,
    val operation: FeatureOperation = FeatureOperation.ADD,
    val parentFeatureId: String? = null
) {
    init {
        require(id.isNotBlank()) { "Feature id cannot be blank" }
        parameters.forEach { (k, v) ->
            require(k.isNotBlank()) { "Feature parameter key cannot be blank" }
        }
        // Non-finite / negative values are reported by ForgeGeometryCompiler.validateGeometry
        // (see ForgeGeometryCompiler.kt). The constructor intentionally tolerates them so
        // the compiler layer can route to placeholder() instead of throwing.
    }
}

/**
 * Conjunto de dimensiones explícitas de la pieza. Cualquier campo ausente es null
 * (se valida en el ValidationEngine — DIMENSION_MISSING).
 */
@Serializable
data class DimensionSet(
    val lengthMm: Double? = null,
    val widthMm: Double? = null,
    val heightMm: Double? = null,
    val diameterMm: Double? = null,
    val innerDiameterMm: Double? = null,
    val outerDiameterMm: Double? = null,
    val thicknessMm: Double? = null,
    val toleranceMm: Double? = null,
    val customDimensions: Map<String, Double> = emptyMap()
) {
    init {
        // Dimension invariants are enforced by ForgeGeometryCompiler.validateGeometry
        // and the ValidationEngine, not here. The domain model is a transport object
        // that can carry any user-supplied value; the compiler layer is the single
        // source of truth for what is valid.
    }

    /** Volumen estimado en mm³ a partir de dimensiones básicas (V1: solo bounding primitives). */
    fun estimatedVolumeMm3(): Double? {
        val l = lengthMm ?: return null
        val w = widthMm ?: return null
        val h = heightMm ?: thicknessMm ?: return null
        return l * w * h
    }
}

@Serializable
enum class ConnectionPortType {
    BOLT_HOLE, SHAFT, BEARING_SEAT, BUSHING, WELD_SURFACE, SLIDING_RAIL,
    HINGE_AXIS, PIPE_PORT, ELECTRICAL_CONNECTOR, FLUID_PORT, BELT_PULLEY, GEAR_AXIS,
    /** Pasador cilíndrico alineado con un eje (e.g. pistón, pivote). */
    PIN,
    /** Chaveta plana — transferencia de par con desplazamiento axial restringido. */
    KEY,
    /** Ranura para cuña o prisionero. */
    KEYWAY,
    /** Anillo de sellado (O-ring, retén, etc.). */
    SEAL_GROOVE,
    /** Dentado interno/externo para engrane fijo (spline). */
    SPLINE,
    /** Collar/prisionero que se monta sobre un eje. */
    COLLAR
}

@Serializable
data class BoltPattern(
    val count: Int,
    val boltCircleDiameterMm: Double,
    val holeDiameterMm: Double,
    val angleOffsetDeg: Double = 0.0
) {
    init {
        require(count in 1..64) { "BoltPattern.count must be in 1..64" }
        require(boltCircleDiameterMm > 0.0) { "boltCircleDiameterMm must be > 0" }
        require(holeDiameterMm > 0.0) { "holeDiameterMm must be > 0" }
        require(angleOffsetDeg.isFinite()) { "angleOffsetDeg must be finite" }
    }
}

@Serializable
data class ConnectionPort(
    val id: String,
    val name: String = "",
    val portType: ConnectionPortType,
    val position: Vector3Data = Vector3Data.ZERO,
    val normal: Vector3Data = Vector3Data.UNIT_Z,
    val diameterMm: Double? = null,
    val boltPattern: BoltPattern? = null,
    val compatibleJointTypes: List<JointType> = emptyList(),
    val loadRatingN: Double? = null,
    val torqueLimitNm: Double? = null
) {
    init {
        require(id.isNotBlank()) { "ConnectionPort id cannot be blank" }
        diameterMm?.let { require(it > 0.0) { "diameterMm must be > 0" } }
        loadRatingN?.let { require(it >= 0.0 && it.isFinite()) { "loadRatingN must be non-negative finite" } }
        torqueLimitNm?.let { require(it >= 0.0 && it.isFinite()) { "torqueLimitNm must be non-negative finite" } }
    }
}

/**
 * Modelo de daño asociado a la pieza (no runtime — describe cómo puede dañarse).
 */
@Serializable
data class DamageModel(
    val possibleDamageTypes: List<DamageType> = emptyList(),
    val healthDecayPerHour: Double = 0.0,
    val notes: String = ""
) {
    init {
        require(healthDecayPerHour.isFinite() && healthDecayPerHour >= 0.0) {
            "healthDecayPerHour must be non-negative finite"
        }
    }
}

/**
 * Pieza paramétrica: cabecera ForgeArtifact + featureTree + dimensiones + puertos + daño.
 * Regla: featureTree es la fuente de verdad. La malla se deriva, no se guarda.
 */
@Serializable
data class ForgePart(
    val artifact: ForgeArtifact,
    val featureTree: List<ParametricFeature> = emptyList(),
    val materialId: String? = null,
    val manufacturingProcessIds: List<String> = emptyList(),
    val dimensions: DimensionSet = DimensionSet(),
    val massEstimateKg: Double? = null,
    val centerOfMass: Vector3Data? = null,
    val connectionPorts: List<ConnectionPort> = emptyList(),
    val damageModel: DamageModel? = null,
    val repairProcedures: List<RepairProcedure> = emptyList(),
    val replacementProcedures: List<ReplacementProcedure> = emptyList(),
    val relatedDtcCodes: List<String> = emptyList(),
    val renderCacheKey: String? = null
) {
    init {
        require(artifact.artifactType == ForgeArtifactType.PART) {
            "ForgePart.artifact must be of type PART"
        }
        require(featureTree.size <= 256) { "featureTree cannot exceed 256 features (security limit)" }
        val featureIds = featureTree.map { it.id }
        require(featureIds.toSet().size == featureIds.size) { "Feature ids must be unique" }
        massEstimateKg?.let {
            require(it.isFinite() && it >= 0.0) { "massEstimateKg must be non-negative finite" }
        }
    }
}
