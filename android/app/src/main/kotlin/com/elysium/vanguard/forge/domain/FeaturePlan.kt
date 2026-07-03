package com.elysium.vanguard.forge.domain

import kotlinx.serialization.Serializable

/**
 * Plan multi-feature: genera una lista de [ParametricFeature]s a partir de un
 * preset (o varios) siguiendo una estrategia estructural (lineal, circular,
 * composición).
 *
 * Diseño por **composición**: `CompositePlan` puede contener otros planes,
 * incluyendo otros `CompositePlan`. Esto permite motores V6/V8/V10/V12,
 * bóxer, híbridos, transmisiones, suspensiones, etc. sin reescribir lógica.
 *
 * `internal` por ahora: el módulo `forge` es el único que compone planes.
 * Si en el futuro se quieren templates JSON serializables cross-module,
 * se eleva a `public` (ver ADR 0001 — sección Compatibilidad).
 *
 * **Reglas arquitectónicas (ver ADR 0001)**:
 *  - Pureza: `instantiate()` no tiene I/O ni estado mutable.
 *  - Determinismo: mismas entradas → mismo output.
 *  - Composicionalidad: cualquier plan contiene otros planes.
 *  - Serializabilidad: cada subclase es `@Serializable` para guardar/cargar
 *    plantillas en JSON.
 *  - Backward-compat: el `FeaturePreset` single sigue funcionando igual.
 */
internal sealed interface FeaturePlan {

    /**
     * Genera la lista de `ParametricFeature` que este plan representa.
     *
     * Las features resultantes tienen IDs únicos derivados del nombre del plan +
     * índice de instancia. Llamadas repetidas con los mismos parámetros producen
     * la misma lista (mismo contenido).
     */
    fun instantiate(position: Vector3Data = Vector3Data.ZERO): List<ParametricFeature>
}

/**
 * Plan unitario: genera un único `ParametricFeature` a partir de un preset.
 *
 * Es el caso más simple — equivalente a usar `FeaturePreset` directamente, pero
 * sirve como "hoja" de los `CompositePlan` y mantiene la API uniforme.
 */
@Serializable
internal data class SingleFeaturePlan(
    val preset: FeaturePreset,
    val positionOffset: Vector3Data = Vector3Data.ZERO
) : FeaturePlan {
    override fun instantiate(position: Vector3Data): List<ParametricFeature> {
        val combined = Vector3Data(
            x = position.x + positionOffset.x,
            y = position.y + positionOffset.y,
            z = position.z + positionOffset.z
        )
        // ID incluye la posición efectiva para que dos SingleFeaturePlans
        // con el mismo preset pero distinto offset produzcan IDs distintos.
        // Mantiene el determinismo: misma entrada → mismo ID.
        val posKey = "${combined.x.toInt()}_${combined.y.toInt()}_${combined.z.toInt()}"
        val displayKey = preset.displayName.lowercase()
            .replace(' ', '_')
            .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
            .replace('ñ', 'n')
        return listOf(
            ParametricFeature(
                id = "${preset.type.name.lowercase()}_${displayKey}_single_${posKey}",
                type = preset.type,
                name = preset.displayName,
                parameters = preset.defaultParameters,
                position = combined
            )
        )
    }
}

/**
 * Plan lineal: genera N copias del preset espaciadas uniformemente sobre un eje.
 *
 * Ejemplos:
 *  - 4 cilindros en línea: `LinearArrayPlan(cylBore, count=4, spacing=88.0, axis=X)`
 *  - fila de engranajes: `LinearArrayPlan(gear, count=6, spacing=15.0, axis=Y)`
 */
@Serializable
internal data class LinearArrayPlan(
    val preset: FeaturePreset,
    val count: Int,
    val spacing: Double,
    val axis: Axis
) : FeaturePlan {

    /**
     * Eje cartesiano sobre el que se distribuyen las instancias.
     * Independiente del sistema de coordenadas local — `X` siempre es X.
     */
    @Serializable
    enum class Axis(val unit: Vector3Data) {
        X(Vector3Data(1.0, 0.0, 0.0)),
        Y(Vector3Data(0.0, 1.0, 0.0)),
        Z(Vector3Data(0.0, 0.0, 1.0))
    }

    init {
        require(count >= 0) { "count debe ser >= 0, fue $count" }
        require(spacing >= 0.0) { "spacing debe ser >= 0, fue $spacing" }
    }

    override fun instantiate(position: Vector3Data): List<ParametricFeature> {
        if (count == 0) return emptyList()
        return (0 until count).map { i ->
            val offset = Vector3Data(
                x = axis.unit.x * spacing * i,
                y = axis.unit.y * spacing * i,
                z = axis.unit.z * spacing * i
            )
            val total = Vector3Data(
                x = position.x + offset.x,
                y = position.y + offset.y,
                z = position.z + offset.z
            )
            // ID incluye eje + spacing + count + index para que arrays paralelos
            // con distintos parámetros no colisionen incluso si sus primeras
            // posiciones coinciden.
            val posKey = "${total.x.toInt()}_${total.y.toInt()}_${total.z.toInt()}"
            val displayKey = preset.displayName.lowercase()
                .replace(' ', '_')
                .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
                .replace('ñ', 'n')
            ParametricFeature(
                id = "${preset.type.name.lowercase()}_${displayKey}_${axis.name}_s${spacing.toInt()}_n$count@$i@$posKey",
                type = preset.type,
                name = "${preset.displayName} #$i",
                parameters = preset.defaultParameters,
                position = total
            )
        }
    }
}

/**
 * Plan circular: genera N copias del preset distribuidas uniformemente sobre una
 * circunferencia. Pensado para motores en V, arreglos radiales de cilindros, etc.
 *
 * `axis` define el plano del círculo:
 *  - `Y_PERPENDICULAR`: el círculo vive en el plano XZ, perpendicular a Y.
 *    Este es el caso típico de los motores V donde los cilindros "abren" hacia
 *    arriba (Y+) en dos bancadas opuestas.
 *
 * `startAngleRad` es el ángulo inicial en radianes. Por defecto 0.0 — la primera
 * instancia queda sobre el eje X+.
 */
@Serializable
internal data class CircularPatternPlan(
    val preset: FeaturePreset,
    val count: Int,
    val radius: Double,
    val axis: Axis = Axis.Y_PERPENDICULAR,
    val startAngleRad: Double = 0.0
) : FeaturePlan {

    @Serializable
    enum class Axis {
        /** Círculo en plano XZ, eje Y perpendicular (motores V típicos). */
        Y_PERPENDICULAR
    }

    init {
        require(count >= 0) { "count debe ser >= 0, fue $count" }
        require(radius >= 0.0) { "radius debe ser >= 0, fue $radius" }
    }

    override fun instantiate(position: Vector3Data): List<ParametricFeature> {
        if (count == 0 || radius == 0.0) return emptyList()
        return (0 until count).map { i ->
            val angleRad = startAngleRad + (2.0 * Math.PI / count) * i
            val offset = when (axis) {
                Axis.Y_PERPENDICULAR -> Vector3Data(
                    x = Math.cos(angleRad) * radius,
                    y = 0.0,
                    z = Math.sin(angleRad) * radius
                )
            }
            val total = Vector3Data(
                x = position.x + offset.x,
                y = position.y + offset.y,
                z = position.z + offset.z
            )
            // ID incluye radio para que dos círculos en el mismo centro con
            // distinto radio no colisionen.
            val posKey = "${total.x.toInt()}_${total.y.toInt()}_${total.z.toInt()}"
            val displayKey = preset.displayName.lowercase()
                .replace(' ', '_')
                .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
                .replace('ñ', 'n')
            ParametricFeature(
                id = "${preset.type.name.lowercase()}_${displayKey}_circ_r${radius.toInt()}_$i@$posKey",
                type = preset.type,
                name = "${preset.displayName} #$i",
                parameters = preset.defaultParameters,
                position = total
            )
        }
    }
}

/**
 * Plan compuesto: agrupa otros planes y suma sus features resultantes.
 *
 * Si `centerOrigin == true`, el bounding box de los hijos se centra automáticamente
 * en (0, 0, 0). Útil para que motores no aparezcan descentrados en el editor.
 *
 * La composición es recursiva: un `CompositePlan` puede contener otros
 * `CompositePlan`. Esto habilita plantillas jerárquicas tipo:
 *
 * ```
 * Motor = CompositePlan {
 *     Block,          // SingleFeaturePlan
 *     CylinderBank,   // CompositePlan { 4 × LinearArray }
 *     CrankShaft      // SingleFeaturePlan
 * }
 * ```
 */
@Serializable
internal data class CompositePlan(
    val name: String,
    val children: List<FeaturePlan>,
    val centerOrigin: Boolean = false
) : FeaturePlan {

    override fun instantiate(position: Vector3Data): List<ParametricFeature> {
        if (children.isEmpty()) return emptyList()

        val raw = children.flatMap { it.instantiate(position) }

        if (!centerOrigin) return raw

        // Calcular el centroide de las features generadas y restarlo.
        val (cx, cy, cz) = computeCentroid(raw)
        return raw.map { f ->
            f.copy(
                position = Vector3Data(
                    x = f.position.x - cx,
                    y = f.position.y - cy,
                    z = f.position.z - cz
                )
            )
        }
    }

    private fun computeCentroid(features: List<ParametricFeature>): Triple<Double, Double, Double> {
        if (features.isEmpty()) return Triple(0.0, 0.0, 0.0)
        val n = features.size.toDouble()
        val sx = features.sumOf { it.position.x } / n
        val sy = features.sumOf { it.position.y } / n
        val sz = features.sumOf { it.position.z } / n
        return Triple(sx, sy, sz)
    }
}