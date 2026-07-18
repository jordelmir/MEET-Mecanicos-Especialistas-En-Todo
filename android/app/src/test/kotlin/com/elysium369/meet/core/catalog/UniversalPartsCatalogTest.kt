package com.elysium369.meet.core.catalog

import java.io.File
import com.elysium369.meet.core.engine3d.ElysiumProceduralModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalPartsCatalogTest {
    private val pack: UniversalPartsPack by lazy {
        val candidates = listOf(
            File("src/main/assets/$UNIVERSAL_PARTS_ASSET"),
            File("app/src/main/assets/$UNIVERSAL_PARTS_ASSET"),
            File("android/app/src/main/assets/$UNIVERSAL_PARTS_ASSET")
        )
        val asset = candidates.firstOrNull(File::isFile)
            ?: error("Pilot catalog asset was not found from ${File(".").absolutePath}")
        UniversalPartsCatalogParser.decode(asset.readText())
    }

    @Test
    fun `asset contains 50 conservative source-backed parts`() {
        assertEquals(50, pack.parts.size)
        assertEquals(50, pack.parts.map { it.id }.distinct().size)
        pack.parts.forEach { part ->
            assertEquals("UNVERIFIED", part.confidence)
            assertEquals("REVIEW_REQUIRED", part.publicationState)
            assertEquals("REQUIRES_VERIFICATION", part.compatibilityState)
            assertTrue(part.sourceRefs.isNotEmpty())
            assertNull(part.technicalSpecifications.oemNumber)
            assertNull(part.technicalSpecifications.torque)
            assertEquals(part.id, part.threeDimensionalBinding.nodeId)
            assertEquals("GENERIC_SCHEMATIC", part.threeDimensionalBinding.visualAuthority)
        }
    }

    @Test
    fun `search resolves regional control arm aliases`() {
        listOf("tijereta", "trapecio", "lower control arm").forEach { query ->
            val results = UniversalPartsCatalogEngine.search(pack.parts, query).map { it.id }
            assertTrue(query, "front_left_lower_control_arm" in results)
            assertTrue(query, "front_right_lower_control_arm" in results)
        }
    }

    @Test
    fun `3D scene and catalog use the same semantic IDs`() {
        val catalogIds = pack.parts.map { it.id }.toSet()
        assertEquals(catalogIds, ElysiumProceduralModels.FRONT_SUSPENSION_NODE_IDS)
        assertEquals(catalogIds, ElysiumProceduralModels.buildFrontSuspensionScene().map { it.id }.toSet())
    }

    @Test
    fun `compatibility never becomes exact from profile selection`() {
        val part = pack.parts.first { it.id == "front_left_lower_control_arm" }
        val assessment = UniversalPartsCatalogEngine.assessCompatibility(
            part,
            part.requiredCompatibilityEvidence.toSet()
        )
        assertEquals("REQUIRES_VERIFICATION", assessment.state)
        assertTrue(assessment.missingEvidence.isEmpty())
    }

    @Test
    fun `torque step is blocked without a verified claim`() {
        val torque = pack.procedures.flatMap { it.steps }
            .first { it.completionGate == "VERIFIED_TORQUE_REQUIRED" }
        assertNull(torque.technicalValue)
        val result = UniversalPartsCatalogEngine.canCompleteStep(
            torque,
            torque.requiredEvidence.toSet(),
            hasVerifiedTechnicalClaim = false
        )
        assertFalse(result.allowed)
        assertTrue(result.reason.orEmpty().contains("no confirmado", ignoreCase = true))
    }

    @Test
    fun `progress advances manual inspection and persists blocked state`() {
        val procedure = pack.procedures.first { it.id.startsWith("inspect_") }
        val initial = RepairProgress(procedure.id, pack.packVersion)
        val advanced = RepairProgressEngine.toggleStep(initial, procedure, procedure.steps.first().id, nowEpochMillis = 1L)
        assertEquals("IN_PROGRESS", advanced.progress.state)
        assertEquals(setOf(procedure.steps.first().id), advanced.progress.completedStepIds)

        val replacement = pack.procedures.first { it.id.startsWith("replace_") }
        val torque = replacement.steps.first { it.completionGate == "VERIFIED_TORQUE_REQUIRED" }
        val blocked = RepairProgressEngine.toggleStep(
            RepairProgress(replacement.id, pack.packVersion),
            replacement,
            torque.id,
            torque.requiredEvidence.toSet(),
            hasVerifiedTechnicalClaim = false,
            nowEpochMillis = 2L
        )
        assertEquals("BLOCKED", blocked.progress.state)
        assertEquals(torque.id, blocked.progress.blockedStepId)
        assertFalse(blocked.progress.completedStepIds.contains(torque.id))
    }
}
