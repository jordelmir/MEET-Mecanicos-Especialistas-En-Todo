package com.elysium.vanguard.forge.education

import kotlinx.serialization.Serializable

/**
 * ASTRA V6 §20 — FORGE Education Worlds (non-automotive).
 *
 * First non-automotive FORGE world: Math 1.º February (Costa Rica MEP 2026).
 * A 3D room with movable objects for spatial position learning:
 * encima/debajo, izquierda/derecha, dentro/fuera, cerca/lejos.
 *
 * This proves FORGE can generalize beyond automotive 3D twin into
 * any educational spatial environment.
 */

// ─── World Primitives ───

@Serializable
data class ForgeWorldId(val value: String) {
    init { require(value.isNotBlank()) { "WorldId cannot be blank" } }
}

@Serializable
data class ForgeTransform(
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val positionZ: Float = 0f,
    val rotationDegrees: Float = 0f,
    val scale: Float = 1f,
)

@Serializable
data class ForgeWorldEntity(
    val entityId: String,
    val label: String,
    val semanticId: String,
    val transform: ForgeTransform = ForgeTransform(),
    val isInteractive: Boolean = true,
    val isVisible: Boolean = true,
    val meshType: ForgeMeshType = ForgeMeshType.CUBE,
    val colorHex: String = "#4A90D9",
    val tags: List<String> = emptyList(),
)

enum class ForgeMeshType {
    CUBE, SPHERE, CYLINDER, PLANE, CONE, CUSTOM_GLTF,
}

// ─── Spatial Relation Engine ───

enum class SpatialRelation(val spanishLabel: String) {
    ABOVE("encima de"),
    BELOW("debajo de"),
    LEFT_OF("a la izquierda de"),
    RIGHT_OF("a la derecha de"),
    INSIDE("dentro de"),
    OUTSIDE("fuera de"),
    NEAR("cerca de"),
    FAR("lejos de"),
    IN_FRONT_OF("delante de"),
    BEHIND("detrás de"),
    BETWEEN("entre"),
    ON_TOP_OF("sobre"),
}

object SpatialRelationEngine {

    private const val NEAR_THRESHOLD = 2.0f
    private const val FAR_THRESHOLD = 8.0f
    private const val VERTICAL_THRESHOLD = 0.5f
    private const val HORIZONTAL_THRESHOLD = 0.5f

    /**
     * Evaluates the spatial relation between two entities.
     * Used by the Socratic tutor to generate adaptive spatial tasks:
     * "¿Dónde está la pelota?" → student must answer "encima de la mesa"
     */
    fun evaluate(
        subject: ForgeWorldEntity,
        reference: ForgeWorldEntity,
    ): List<SpatialRelation> {
        val dx = subject.transform.positionX - reference.transform.positionX
        val dy = subject.transform.positionY - reference.transform.positionY
        val dz = subject.transform.positionZ - reference.transform.positionZ
        val distance = kotlin.math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()

        val relations = mutableListOf<SpatialRelation>()

        // Vertical relations
        if (dy > VERTICAL_THRESHOLD) relations.add(SpatialRelation.ABOVE)
        if (dy < -VERTICAL_THRESHOLD) relations.add(SpatialRelation.BELOW)

        // Horizontal relations
        if (dx > HORIZONTAL_THRESHOLD) relations.add(SpatialRelation.RIGHT_OF)
        if (dx < -HORIZONTAL_THRESHOLD) relations.add(SpatialRelation.LEFT_OF)

        // Depth relations
        if (dz > HORIZONTAL_THRESHOLD) relations.add(SpatialRelation.IN_FRONT_OF)
        if (dz < -HORIZONTAL_THRESHOLD) relations.add(SpatialRelation.BEHIND)

        // Distance relations
        if (distance < NEAR_THRESHOLD) relations.add(SpatialRelation.NEAR)
        if (distance > FAR_THRESHOLD) relations.add(SpatialRelation.FAR)

        return relations
    }

    /**
     * Validates a student's spatial answer against the actual world state.
     * Returns true if the student correctly identified the relation.
     */
    fun validateAnswer(
        subject: ForgeWorldEntity,
        reference: ForgeWorldEntity,
        studentAnswer: SpatialRelation,
    ): Boolean {
        return studentAnswer in evaluate(subject, reference)
    }
}

// ─── Math 1.º February World (Course Zero) ───

object MathFirstGradeSpatialWorld {

    val WORLD_ID = ForgeWorldId("cr_math_1_feb_spatial")

    /**
     * Creates the canonical scene for Math 1.º February:
     * "Posición de objetos y figuras según nociones espaciales"
     * MEP indicators: Reconocimiento (R), Comprensión (C), Aplicación (Ap)
     */
    fun createScene(): List<ForgeWorldEntity> = listOf(
        // Room reference objects
        ForgeWorldEntity(
            entityId = "mesa_1",
            label = "Mesa",
            semanticId = "TABLE",
            transform = ForgeTransform(positionX = 0f, positionY = 0f, positionZ = 0f),
            meshType = ForgeMeshType.CUBE,
            colorHex = "#8B4513",
            tags = listOf("furniture", "reference"),
        ),
        ForgeWorldEntity(
            entityId = "silla_1",
            label = "Silla",
            semanticId = "CHAIR",
            transform = ForgeTransform(positionX = -1.5f, positionY = 0f, positionZ = 0f),
            meshType = ForgeMeshType.CUBE,
            colorHex = "#A0522D",
            tags = listOf("furniture", "reference"),
        ),
        ForgeWorldEntity(
            entityId = "caja_1",
            label = "Caja",
            semanticId = "BOX",
            transform = ForgeTransform(positionX = 2f, positionY = 0f, positionZ = -1f),
            meshType = ForgeMeshType.CUBE,
            colorHex = "#DAA520",
            tags = listOf("container", "reference"),
        ),

        // Interactive objects (student moves these)
        ForgeWorldEntity(
            entityId = "pelota_roja",
            label = "Pelota roja",
            semanticId = "RED_BALL",
            transform = ForgeTransform(positionX = 0f, positionY = 1.5f, positionZ = 0f),
            meshType = ForgeMeshType.SPHERE,
            colorHex = "#FF4444",
            tags = listOf("movable", "math_spatial"),
        ),
        ForgeWorldEntity(
            entityId = "pelota_azul",
            label = "Pelota azul",
            semanticId = "BLUE_BALL",
            transform = ForgeTransform(positionX = 3f, positionY = 0.5f, positionZ = 2f),
            meshType = ForgeMeshType.SPHERE,
            colorHex = "#4444FF",
            tags = listOf("movable", "math_spatial"),
        ),
        ForgeWorldEntity(
            entityId = "estrella",
            label = "Estrella",
            semanticId = "STAR",
            transform = ForgeTransform(positionX = -2f, positionY = 3f, positionZ = 0f),
            meshType = ForgeMeshType.CONE,
            colorHex = "#FFD700",
            tags = listOf("movable", "math_spatial"),
        ),
        ForgeWorldEntity(
            entityId = "cubo_verde",
            label = "Cubo verde",
            semanticId = "GREEN_CUBE",
            transform = ForgeTransform(positionX = 0f, positionY = 0f, positionZ = -3f),
            meshType = ForgeMeshType.CUBE,
            colorHex = "#44AA44",
            tags = listOf("movable", "math_spatial"),
        ),
        ForgeWorldEntity(
            entityId = "cilindro_naranja",
            label = "Cilindro naranja",
            semanticId = "ORANGE_CYLINDER",
            transform = ForgeTransform(positionX = 1f, positionY = -0.5f, positionZ = 1f),
            meshType = ForgeMeshType.CYLINDER,
            colorHex = "#FF8C00",
            tags = listOf("movable", "math_spatial"),
        ),
    )

    /**
     * Generates a spatial task for the given scene.
     * Example: "¿Dónde está la pelota roja respecto a la mesa?"
     * Expected answer: "encima de" (because Y position is above)
     */
    fun generateSpatialTask(
        scene: List<ForgeWorldEntity>,
        targetRelation: SpatialRelation,
    ): SpatialTask? {
        for (subject in scene.filter { "movable" in it.tags }) {
            for (reference in scene.filter { "reference" in it.tags }) {
                val relations = SpatialRelationEngine.evaluate(subject, reference)
                if (targetRelation in relations) {
                    return SpatialTask(
                        question = "¿Dónde está ${subject.label.lowercase()} respecto a ${reference.label.lowercase()}?",
                        subjectEntityId = subject.entityId,
                        referenceEntityId = reference.entityId,
                        correctAnswer = targetRelation,
                        allValidAnswers = relations,
                        conceptCode = "cr_mat1_feb_nociones_espaciales",
                    )
                }
            }
        }
        return null
    }
}

@Serializable
data class SpatialTask(
    val question: String,
    val subjectEntityId: String,
    val referenceEntityId: String,
    val correctAnswer: SpatialRelation,
    val allValidAnswers: List<SpatialRelation>,
    val conceptCode: String,
)
