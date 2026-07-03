package com.elysium.vanguard.forge.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tests de [FeaturePlan] y sus 4 implementaciones.
 *
 * Cubren:
 *  - **Determinismo**: misma entrada → mismo output, N veces.
 *  - **Geometría correcta**: posiciones siguen la fórmula declarada.
 *  - **Casos límite**: count=0, radius=0, planes vacíos.
 *  - **Composicionalidad**: CompositePlan funciona con planes anidados.
 *  - **centerOrigin**: el centroide de las features queda cerca de (0,0,0).
 */
class FeaturePlanTest {

    /** Preset simple reutilizado por tests (cilindro Ø50×100). */
    private val cylPreset = FeaturePreset(
        type = FeatureType.CYLINDER,
        displayName = "Cilindro Test",
        shortSpec = "Ø50×100",
        defaultParameters = mapOf("diameter" to 50.0, "height" to 100.0)
    )

    // ─────────── SingleFeaturePlan ───────────

    @Test
    fun `SingleFeaturePlan produces exactly one feature`() {
        val plan = SingleFeaturePlan(cylPreset)
        val features = plan.instantiate()
        assertEquals(1, features.size)
        val f = features[0]
        assertEquals(cylPreset.type, f.type)
        assertEquals(cylPreset.displayName, f.name)
        assertEquals(cylPreset.defaultParameters, f.parameters)
        // Posición por defecto = (0, 0, 0).
        assertEquals(0.0, f.position.x, 0.0)
        assertEquals(0.0, f.position.y, 0.0)
        assertEquals(0.0, f.position.z, 0.0)
    }

    @Test
    fun `SingleFeaturePlan respects position and offset`() {
        val offset = Vector3Data(x = 10.0, y = 20.0, z = 30.0)
        val plan = SingleFeaturePlan(cylPreset, positionOffset = offset)
        val features = plan.instantiate(position = Vector3Data(5.0, -5.0, 0.0))
        val f = features[0]
        assertEquals(15.0, f.position.x, 1e-6)
        assertEquals(15.0, f.position.y, 1e-6)
        assertEquals(30.0, f.position.z, 1e-6)
    }

    // ─────────── LinearArrayPlan ───────────

    @Test
    fun `LinearArrayPlan produces N features on chosen axis`() {
        val plan = LinearArrayPlan(
            preset = cylPreset,
            count = 4,
            spacing = 88.0,
            axis = LinearArrayPlan.Axis.X
        )
        val features = plan.instantiate()
        assertEquals(4, features.size)
        // Primera feature en x=0, segunda en x=88, etc.
        assertEquals(0.0, features[0].position.x, 1e-6)
        assertEquals(88.0, features[1].position.x, 1e-6)
        assertEquals(176.0, features[2].position.x, 1e-6)
        assertEquals(264.0, features[3].position.x, 1e-6)
        // Y y Z siempre 0 en eje X.
        features.forEach {
            assertEquals(0.0, it.position.y, 1e-6)
            assertEquals(0.0, it.position.z, 1e-6)
        }
    }

    @Test
    fun `LinearArrayPlan with count 0 returns empty list`() {
        val plan = LinearArrayPlan(cylPreset, count = 0, spacing = 10.0, axis = LinearArrayPlan.Axis.Y)
        assertEquals(emptyList<ParametricFeature>(), plan.instantiate())
    }

    @Test
    fun `LinearArrayPlan rejects negative count`() {
        try {
            LinearArrayPlan(cylPreset, count = -1, spacing = 10.0, axis = LinearArrayPlan.Axis.Z)
            fail("Esperaba IllegalArgumentException para count=-1")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("count"))
        }
    }

    @Test
    fun `LinearArrayPlan is deterministic across invocations`() {
        val plan = LinearArrayPlan(cylPreset, count = 3, spacing = 50.0, axis = LinearArrayPlan.Axis.Y)
        val a = plan.instantiate()
        val b = plan.instantiate()
        assertEquals(a.size, b.size)
        a.zip(b).forEach { (x, y) ->
            assertEquals(x.id, y.id)
            assertEquals(x.position.x, y.position.x, 1e-6)
            assertEquals(x.position.y, y.position.y, 1e-6)
            assertEquals(x.position.z, y.position.z, 1e-6)
        }
    }

    // ─────────── CircularPatternPlan ───────────

    @Test
    fun `CircularPatternPlan with V8 count produces 8 features`() {
        val plan = CircularPatternPlan(
            preset = cylPreset,
            count = 8,
            radius = 80.0,
            axis = CircularPatternPlan.Axis.Y_PERPENDICULAR
        )
        val features = plan.instantiate()
        assertEquals(8, features.size)
    }

    @Test
    fun `CircularPatternPlan distributes evenly on the circle`() {
        val plan = CircularPatternPlan(
            preset = cylPreset,
            count = 6,
            radius = 50.0,
            axis = CircularPatternPlan.Axis.Y_PERPENDICULAR
        )
        val features = plan.instantiate()
        // Todos en plano XZ (y=0).
        features.forEach { assertEquals(0.0, it.position.y, 1e-6) }
        // Cada feature debe estar a distancia ~radius del origen.
        features.forEach {
            val dist = kotlin.math.sqrt(it.position.x * it.position.x + it.position.z * it.position.z)
            assertEquals(50.0, dist, 1e-4)
        }
    }

    @Test
    fun `CircularPatternPlan respects startAngleRad`() {
        // startAngle = π/2 coloca la primera feature en Z+ (en plano XZ).
        val plan = CircularPatternPlan(
            preset = cylPreset,
            count = 4,
            radius = 10.0,
            axis = CircularPatternPlan.Axis.Y_PERPENDICULAR,
            startAngleRad = PI / 2
        )
        val first = plan.instantiate()[0]
        // cos(π/2) = 0, sin(π/2) = 1, radio = 10 → (0, 0, 10).
        assertEquals(0.0, first.position.x, 1e-6)
        assertEquals(0.0, first.position.y, 1e-6)
        assertEquals(10.0, first.position.z, 1e-6)
    }

    @Test
    fun `CircularPatternPlan with count 0 returns empty list`() {
        val plan = CircularPatternPlan(cylPreset, count = 0, radius = 50.0)
        assertEquals(emptyList<ParametricFeature>(), plan.instantiate())
    }

    @Test
    fun `CircularPatternPlan with radius 0 returns empty list`() {
        // radius=0 degenera a círculo de 0 features (todas las posiciones colapsan).
        val plan = CircularPatternPlan(cylPreset, count = 8, radius = 0.0)
        assertEquals(emptyList<ParametricFeature>(), plan.instantiate())
    }

    @Test
    fun `CircularPatternPlan rejects negative radius`() {
        try {
            CircularPatternPlan(cylPreset, count = 4, radius = -10.0)
            fail("Esperaba IllegalArgumentException para radius=-10")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("radius"))
        }
    }

    // ─────────── CompositePlan ───────────

    @Test
    fun `CompositePlan sums child features in order`() {
        val plan = CompositePlan(
            name = "Test composite",
            children = listOf(
                SingleFeaturePlan(cylPreset),
                LinearArrayPlan(cylPreset, count = 3, spacing = 10.0, axis = LinearArrayPlan.Axis.X)
            )
        )
        val features = plan.instantiate()
        assertEquals(4, features.size)  // 1 + 3
    }

    @Test
    fun `CompositePlan with empty children returns empty`() {
        val plan = CompositePlan(name = "Empty", children = emptyList())
        assertEquals(emptyList<ParametricFeature>(), plan.instantiate())
    }

    @Test
    fun `CompositePlan nested composition works recursively`() {
        val inner = CompositePlan(
            name = "Inner",
            children = listOf(
                SingleFeaturePlan(cylPreset),
                LinearArrayPlan(cylPreset, count = 2, spacing = 5.0, axis = LinearArrayPlan.Axis.Z)
            )
        )
        val outer = CompositePlan(
            name = "Outer",
            children = listOf(inner, SingleFeaturePlan(cylPreset))
        )
        val features = outer.instantiate()
        // 1 + 2 + 1 = 4
        assertEquals(4, features.size)
    }

    @Test
    fun `CompositePlan centerOrigin brings centroid to origin`() {
        val plan = CompositePlan(
            name = "Off-center engine",
            children = listOf(
                LinearArrayPlan(cylPreset, count = 5, spacing = 100.0, axis = LinearArrayPlan.Axis.X)
            ),
            centerOrigin = true
        )
        val features = plan.instantiate()
        assertEquals(5, features.size)
        // El centroide tras centerOrigin debe estar muy cerca de (0,0,0).
        val cx = features.map { it.position.x }.average()
        val cy = features.map { it.position.y }.average()
        val cz = features.map { it.position.z }.average()
        assertEquals(0.0, cx, 1e-6)
        assertEquals(0.0, cy, 1e-6)
        assertEquals(0.0, cz, 1e-6)
    }

    @Test
    fun `CompositePlan without centerOrigin preserves original positions`() {
        val plan = CompositePlan(
            name = "As-is engine",
            children = listOf(
                LinearArrayPlan(cylPreset, count = 5, spacing = 100.0, axis = LinearArrayPlan.Axis.X)
            ),
            centerOrigin = false
        )
        val features = plan.instantiate()
        // Sin center, la primera feature queda en x=0 (no en x=-200).
        assertEquals(0.0, features[0].position.x, 1e-6)
        // El centroide queda en x=200 (media de [0, 100, 200, 300, 400]).
        val cx = features.map { it.position.x }.average()
        assertEquals(200.0, cx, 1e-6)
    }

    // ─────────── Determinismo cross-plan ───────────

    @Test
    fun `complex CompositePlan is deterministic across 100 invocations`() {
        val plan = CompositePlan(
            name = "V8 engine block",
            children = listOf(
                SingleFeaturePlan(cylPreset),
                CircularPatternPlan(cylPreset, count = 8, radius = 80.0)
            ),
            centerOrigin = true
        )
        val reference = plan.instantiate()
        for (i in 0 until 100) {
            val call = plan.instantiate()
            assertEquals(reference.size, call.size)
            reference.zip(call).forEach { (a, b) ->
                assertEquals(a.id, b.id)
                assertEquals(a.position.x, b.position.x, 1e-6)
                assertEquals(a.position.y, b.position.y, 1e-6)
                assertEquals(a.position.z, b.position.z, 1e-6)
                assertEquals(a.parameters, b.parameters)
            }
        }
    }

    @Test
    fun `FeaturePlan feature IDs are unique when children have distinct content`() {
        // IDs derivan del preset + parámetros + posición. Children con
        // presets distintos producen IDs distintos. Esto valida que la
        // composición no colapsa IDs de diferentes tipos.
        val presetA = FeaturePreset(
            FeatureType.CYLINDER, "Pieza A", "spec A",
            mapOf("diameter" to 50.0)
        )
        val presetB = FeaturePreset(
            FeatureType.BOX, "Pieza B", "spec B",
            mapOf("length" to 100.0)
        )
        val presetC = FeaturePreset(
            FeatureType.PLATE, "Pieza C", "spec C",
            mapOf("length" to 80.0, "thickness" to 5.0)
        )
        val plan = CompositePlan(
            name = "Distinct children",
            children = listOf(
                SingleFeaturePlan(presetA),
                SingleFeaturePlan(presetB),
                SingleFeaturePlan(presetC)
            )
        )
        val features = plan.instantiate()
        val ids = features.map { it.id }
        assertEquals(3, ids.toSet().size)  // todos distintos por contenido distinto
    }

    @Test
    fun `SingleFeaturePlan with same preset and position produces same ID`() {
        // Las IDs son deterministas: mismo preset + misma posición = mismo ID.
        // Esto es propiedad del sistema (no es bug).
        val plan1 = SingleFeaturePlan(cylPreset)
        val plan2 = SingleFeaturePlan(cylPreset)
        val id1 = plan1.instantiate()[0].id
        val id2 = plan2.instantiate()[0].id
        assertEquals(id1, id2)
    }

    @Test
    fun `LinearArray feature IDs are unique`() {
        val plan = LinearArrayPlan(cylPreset, count = 5, spacing = 10.0, axis = LinearArrayPlan.Axis.X)
        val ids = plan.instantiate().map { it.id }
        assertEquals(5, ids.toSet().size)
    }
}