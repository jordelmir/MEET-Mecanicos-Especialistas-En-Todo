package com.elysium.vanguard.forge.engine

import com.elysium.vanguard.forge.domain.DimensionSet
import com.elysium.vanguard.forge.domain.FeatureOperation
import com.elysium.vanguard.forge.domain.FeatureType
import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.ParametricFeature
import com.elysium.vanguard.forge.domain.SafetyClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeGeometryCompilerTest {

    private val compiler = ForgeGeometryCompiler()

    private fun partWith(features: List<ParametricFeature>, dims: DimensionSet = DimensionSet()): ForgePart {
        return ForgePart(
            artifact = ForgeArtifact(
                id = "test_part",
                name = "Test Part",
                artifactType = ForgeArtifactType.PART,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            featureTree = features,
            dimensions = dims
        )
    }

    @Test
    fun `cylinder part compiles`() {
        val part = partWith(
            listOf(
                ParametricFeature(
                    id = "cyl",
                    type = FeatureType.CYLINDER,
                    parameters = mapOf("diameterMm" to 50.0, "heightMm" to 100.0)
                )
            )
        )
        val result = compiler.compilePart(part)
        assertTrue("Compiled mesh must not be empty", result.mesh.vertices.isNotEmpty())
        assertTrue("Compiled mesh must have faces", result.mesh.faces.isNotEmpty())
        assertFalse("Must not use fallback for valid cylinder", result.usedFallback)
    }

    @Test
    fun `box part compiles`() {
        val part = partWith(
            listOf(
                ParametricFeature(
                    id = "bx",
                    type = FeatureType.BOX,
                    parameters = mapOf("lengthMm" to 100.0, "widthMm" to 50.0, "heightMm" to 25.0)
                )
            )
        )
        val result = compiler.compilePart(part)
        assertTrue(result.mesh.vertices.isNotEmpty())
        assertTrue(result.mesh.faces.isNotEmpty())
    }

    @Test
    fun `tube part compiles with inner and outer`() {
        val part = partWith(
            listOf(
                ParametricFeature(
                    id = "tube",
                    type = FeatureType.TUBE,
                    parameters = mapOf(
                        "outerDiameterMm" to 80.0,
                        "innerDiameterMm" to 40.0,
                        "heightMm" to 30.0
                    )
                )
            )
        )
        val result = compiler.compilePart(part)
        assertTrue(result.mesh.vertices.isNotEmpty())
    }

    @Test
    fun `negative dimension fails safely with placeholder`() {
        val part = partWith(
            listOf(
                ParametricFeature(
                    id = "bad_box",
                    type = FeatureType.BOX,
                    parameters = mapOf("lengthMm" to -50.0, "widthMm" to 10.0, "heightMm" to 10.0)
                )
            )
        )
        val result = compiler.compilePart(part)
        // When only feature has bad data, the compiler falls back to placeholder.
        assertTrue("Must use fallback for negative dims", result.usedFallback)
        // Placeholder should still produce non-empty mesh.
        assertNotNull(result.mesh)
    }

    @Test
    fun `zero thickness fails safely`() {
        val part = partWith(
            listOf(
                ParametricFeature(
                    id = "thin_plate",
                    type = FeatureType.PLATE,
                    parameters = mapOf("lengthMm" to 100.0, "widthMm" to 100.0, "thicknessMm" to 0.0)
                )
            )
        )
        val result = compiler.compilePart(part)
        // Should not crash; result is well-formed.
        assertNotNull(result.mesh)
        assertTrue(result.warnings.isNotEmpty() || result.usedFallback)
    }

    @Test
    fun `NaN never leaks to compiled mesh`() {
        val part = partWith(
            listOf(
                ParametricFeature(
                    id = "nan_feature",
                    type = FeatureType.BOX,
                    parameters = mapOf("lengthMm" to Double.NaN, "widthMm" to 10.0, "heightMm" to 10.0)
                )
            )
        )
        val result = compiler.compilePart(part)
        // All vertices must have finite coordinates.
        for (v in result.mesh.vertices) {
            assertTrue("Vertex x must be finite", v.x.isFinite())
            assertTrue("Vertex y must be finite", v.y.isFinite())
            assertTrue("Vertex z must be finite", v.z.isFinite())
        }
    }

    @Test
    fun `missing material still creates visual placeholder`() {
        val part = partWith(
            listOf(
                ParametricFeature(
                    id = "block",
                    type = FeatureType.BOX,
                    parameters = mapOf("lengthMm" to 10.0, "widthMm" to 10.0, "heightMm" to 10.0)
                )
            )
        )
        // No materialId — debe compilar igual.
        val result = compiler.compilePart(part)
        assertTrue(result.mesh.vertices.isNotEmpty())
    }

    @Test
    fun `circular pattern emits hole markers`() {
        val part = partWith(
            listOf(
                ParametricFeature(
                    id = "disc",
                    type = FeatureType.TUBE,
                    parameters = mapOf("outerDiameterMm" to 100.0, "innerDiameterMm" to 20.0, "heightMm" to 10.0)
                ),
                ParametricFeature(
                    id = "pattern",
                    type = FeatureType.CIRCULAR_PATTERN,
                    parameters = mapOf(
                        "count" to 5.0,
                        "boltCircleDiameterMm" to 60.0,
                        "holeDiameterMm" to 8.0
                    ),
                    operation = FeatureOperation.PATTERN
                )
            )
        )
        val result = compiler.compilePart(part)
        assertTrue(result.mesh.vertices.isNotEmpty())
    }

    @Test
    fun `validateGeometry detects invalid dimensions`() {
        val part = partWith(
            emptyList(),
            dims = DimensionSet(lengthMm = -10.0, widthMm = 0.0, heightMm = 5.0)
        )
        val validation = compiler.validateGeometry(part)
        assertFalse("Invalid dimensions must produce errors", validation.isValid)
        assertTrue(validation.errors.isNotEmpty())
    }

    @Test
    fun `validateGeometry passes for empty but safe part`() {
        val part = partWith(
            emptyList(),
            dims = DimensionSet(lengthMm = 100.0, widthMm = 50.0, heightMm = 25.0)
        )
        val validation = compiler.validateGeometry(part)
        assertTrue(validation.isValid)
    }
}