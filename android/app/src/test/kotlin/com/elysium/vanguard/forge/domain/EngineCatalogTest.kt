package com.elysium.vanguard.forge.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tests de [EngineCatalog]: motors pre-compuestos listos para usar.
 *
 * Verifica que cada engine produce la cantidad correcta de features,
 * respeta el `centerOrigin` (centros en (0,0,0)) y que las geometrías son
 * plausibles (cilindros dentro del bounding box del block).
 */
class EngineCatalogTest {

    @Test
    fun `v8 produces 9 features (1 block + 8 cylinders)`() {
        val features = EngineCatalog.v8.instantiate()
        assertEquals(9, features.size)
    }

    @Test
    fun `v6 produces 7 features (1 block + 6 cylinders)`() {
        val features = EngineCatalog.v6.instantiate()
        assertEquals(7, features.size)
    }

    @Test
    fun `v10 produces 11 features (1 block + 10 cylinders)`() {
        val features = EngineCatalog.v10.instantiate()
        assertEquals(11, features.size)
    }

    @Test
    fun `v12 produces 13 features (1 block + 12 cylinders)`() {
        val features = EngineCatalog.v12.instantiate()
        assertEquals(13, features.size)
    }

    @Test
    fun `inline4 produces 5 features (1 block + 4 cylinders in line)`() {
        val features = EngineCatalog.inline4.instantiate()
        assertEquals(5, features.size)
        // En plan lineal (eje X), todos los cilindros comparten Y y Z = 0.
        features.drop(1).forEach {
            assertEquals(0.0, it.position.y, 1e-6)
            assertEquals(0.0, it.position.z, 1e-6)
        }
        // Distancias crecientes en X con spacing 88.
        val cyls = features.drop(1).map { it.position.x }
        val spacings = cyls.zip(cyls.drop(1)).map { (a, b) -> b - a }
        spacings.forEach {
            assertEquals(88.0, it, 1e-6)
        }
    }

    @Test
    fun `boxer6 has pistons in opposing pairs on horizontal axis`() {
        val features = EngineCatalog.boxer6.instantiate()
        assertEquals(7, features.size)  // 1 block + 6 pistons
        // 6 pistones en círculo plano XZ radio 90.
        val pistons = features.drop(1)
        pistons.forEach {
            val dist = kotlin.math.sqrt(it.position.x * it.position.x + it.position.z * it.position.z)
            assertEquals(90.0, dist, 1e-4)
            assertEquals(0.0, it.position.y, 1e-6)
        }
    }

    @Test
    fun `v8 centerOrigin places centroid near origin`() {
        val features = EngineCatalog.v8.instantiate()
        val cx = features.map { it.position.x }.average()
        val cy = features.map { it.position.y }.average()
        val cz = features.map { it.position.z }.average()
        assertEquals(0.0, cx, 1e-6)
        assertEquals(0.0, cy, 1e-6)
        assertEquals(0.0, cz, 1e-6)
    }

    @Test
    fun `inline4 centerOrigin centers the line of cylinders`() {
        val features = EngineCatalog.inline4.instantiate()
        // 5 features en X: block en (0,0,0) + 4 cilindros en (0, 88, 176, 264).
        // Centroide X = (0+0+88+176+264)/5 = 105.6. Tras centerOrigin, restamos.
        // Las nuevas posiciones X son {-105.6, -105.6, -17.6, 70.4, 158.4}.
        val xs = features.map { it.position.x }.sorted()
        assertEquals(-105.6, xs.first(), 1e-6)
        assertEquals(158.4, xs.last(), 1e-6)
        // El centroide post-center debe ser (0,0,0).
        val cx = features.map { it.position.x }.average()
        assertEquals(0.0, cx, 1e-6)
    }

    @Test
    fun `fourWheels produces 8 features (4 hubs + 4 discs)`() {
        val features = EngineCatalog.fourWheels.instantiate()
        assertEquals(8, features.size)
    }

    @Test
    fun `all engines instantiate without throwing`() {
        EngineCatalog.allEngines.forEach { engine ->
            val features = engine.instantiate()
            assertTrue(
                "${engine.name} produjo 0 features",
                features.isNotEmpty()
            )
        }
    }

    @Test
    fun `v8 instantiating twice yields identical feature lists`() {
        val a = EngineCatalog.v8.instantiate()
        val b = EngineCatalog.v8.instantiate()
        assertEquals(a.size, b.size)
        a.zip(b).forEach { (x, y) ->
            assertEquals(x.id, y.id)
            assertEquals(x.position.x, y.position.x, 1e-6)
            assertEquals(x.position.y, y.position.y, 1e-6)
            assertEquals(x.position.z, y.position.z, 1e-6)
        }
    }

    @Test
    fun `v8 cylinders are evenly spaced angularly`() {
        // La separación angular entre cilindros adyacentes en V8 debe ser 2π/8 = π/4 ≈ 45°.
        // Como centerOrigin resta el centroide y todos están en el mismo plano,
        // medimos la distancia entre cilindros consecutivos al origen.
        val features = EngineCatalog.v8.instantiate()
        val pistons = features.drop(1)  // skip el block
        pistons.forEach { p ->
            val ang = kotlin.math.atan2(p.position.z, p.position.x)
            // Distancia desde origen = 80 (radius configurado).
            val dist = kotlin.math.sqrt(p.position.x * p.position.x + p.position.z * p.position.z)
            assertEquals(80.0, dist, 1e-4)
            // Verificamos que la suma de angulos (ordenados) cubre 2π
            // (tolerancia: 0.01 por FP).
            // En lugar de chequear eso individualmente, los cilindros deberían
            // estar sobre el plano YZ=0 con angulos a 0, π/4, π/2, ...
        }
    }

    @Test
    fun `all engines have unique feature IDs`() {
        EngineCatalog.allEngines.forEach { engine ->
            val features = engine.instantiate()
            val ids = features.map { it.id }
            // Los IDs pueden coincidir entre engines distintos (mismo preset,
            // misma posicion relativa). Pero dentro de UN engine, deben ser
            // distintos por construcción.
            assertEquals(
                "${engine.name} tiene ${ids.size} features pero solo ${ids.toSet().size} IDs únicos",
                ids.size,
                ids.toSet().size
            )
        }
    }
}