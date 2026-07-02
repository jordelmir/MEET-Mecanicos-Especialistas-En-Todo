package com.elysium.vanguard.forge.assembly

import com.elysium.vanguard.forge.domain.DimensionSet
import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.ForgeValidationError
import com.elysium.vanguard.forge.domain.JointType
import com.elysium.vanguard.forge.domain.MechanicalJoint
import com.elysium.vanguard.forge.domain.SafetyClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeAssemblyEngineTest {

    private val engine = ForgeAssemblyEngine()

    private fun emptyAssembly(): ForgeAssembly {
        return ForgeAssembly(
            artifact = ForgeArtifact(
                id = "asm",
                name = "Test Assembly",
                artifactType = ForgeArtifactType.ASSEMBLY,
                safetyClassification = SafetyClassification.EDUCATIONAL
            )
        )
    }

    @Test
    fun `add part to assembly creates new instance`() {
        val asm = emptyAssembly()
        val updated = engine.addPart(asm, "part_a", "instance_a")
        assertEquals(1, updated.instances.size)
        assertEquals("instance_a", updated.instances.first().id)
        assertEquals("part_a", updated.instances.first().partId)
    }

    @Test
    fun `add part is idempotent on duplicate instance id`() {
        val asm = emptyAssembly()
        val once = engine.addPart(asm, "part_a", "instance_a")
        val twice = engine.addPart(once, "part_b", "instance_a")
        assertEquals(1, twice.instances.size)
        assertEquals("part_a", twice.instances.first().partId)
    }

    @Test
    fun `create fixed joint succeeds`() {
        val asm = emptyAssembly()
            .let { engine.addPart(it, "p_a", "i_a") }
            .let { engine.addPart(it, "p_b", "i_b") }
        val result = engine.createJoint(
            assembly = asm,
            jointId = "j1",
            name = "fixed",
            jointType = JointType.FIXED,
            parentInstanceId = "i_a",
            childInstanceId = "i_b"
        )
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().joints.size)
    }

    @Test
    fun `create revolute joint succeeds`() {
        val asm = emptyAssembly()
            .let { engine.addPart(it, "p_a", "i_a") }
            .let { engine.addPart(it, "p_b", "i_b") }
        val result = engine.createJoint(
            assembly = asm,
            jointId = "j1",
            name = "revolute",
            jointType = JointType.REVOLUTE,
            parentInstanceId = "i_a",
            childInstanceId = "i_b"
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun `detect floating parts works`() {
        val asm = emptyAssembly()
            .let { engine.addPart(it, "p_a", "i_a") }
            .let { engine.addPart(it, "p_b", "i_b") }
            .let { engine.addPart(it, "p_c", "i_c") }
            .let {
                engine.createJoint(it, "j1", "", JointType.FIXED, "i_a", "i_b").getOrThrow()
            }
        // i_c no tiene joints → floating.
        val floating = engine.findFloatingParts(asm)
        assertTrue("i_c should be floating", "i_c" in floating)
        assertFalse("i_a should not be floating", "i_a" in floating)
    }

    @Test
    fun `detect incompatible joint when ports do not allow joint type`() {
        val part = ForgePart(
            artifact = ForgeArtifact(
                id = "p_a", name = "P A", artifactType = ForgeArtifactType.PART,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            dimensions = DimensionSet(lengthMm = 10.0, widthMm = 10.0, heightMm = 10.0),
            connectionPorts = listOf(
                com.elysium.vanguard.forge.domain.ConnectionPort(
                    id = "port_a", name = "p", portType = com.elysium.vanguard.forge.domain.ConnectionPortType.BOLT_HOLE,
                    compatibleJointTypes = listOf(JointType.BOLTED, JointType.FIXED)
                )
            )
        )
        val partB = ForgePart(
            artifact = ForgeArtifact(
                id = "p_b", name = "P B", artifactType = ForgeArtifactType.PART,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            dimensions = DimensionSet(lengthMm = 10.0, widthMm = 10.0, heightMm = 10.0),
            connectionPorts = listOf(
                com.elysium.vanguard.forge.domain.ConnectionPort(
                    id = "port_b", name = "p", portType = com.elysium.vanguard.forge.domain.ConnectionPortType.BOLT_HOLE,
                    compatibleJointTypes = listOf(JointType.BOLTED, JointType.FIXED)
                )
            )
        )
        val partsById = mapOf("p_a" to part, "p_b" to partB)
        var asm = emptyAssembly()
        asm = engine.addPart(asm, "p_a", "i_a")
        asm = engine.addPart(asm, "p_b", "i_b")
        // Intentar crear joint REVOLUTE con puerto BOLT_HOLE → debe fallar.
        val result = engine.createJoint(
            assembly = asm,
            jointId = "j1",
            name = "",
            jointType = JointType.REVOLUTE,
            parentInstanceId = "i_a",
            childInstanceId = "i_b",
            parentPortId = "port_a",
            childPortId = "port_b",
            partsById = partsById
        )
        assertTrue("Incompatible joint must fail", result.isFailure)
    }

    @Test
    fun `detect simple interference between overlapping instances`() {
        val part = ForgePart(
            artifact = ForgeArtifact(
                id = "p", name = "P", artifactType = ForgeArtifactType.PART,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            dimensions = DimensionSet(lengthMm = 100.0, widthMm = 100.0, heightMm = 100.0)
        )
        val partsById = mapOf("p" to part)
        var asm = emptyAssembly()
        // Dos piezas grandes superpuestas en el origen.
        asm = engine.addPart(asm, "p", "i_a",
            com.elysium.vanguard.forge.domain.TransformData())
        asm = engine.addPart(asm, "p", "i_b",
            com.elysium.vanguard.forge.domain.TransformData())
        val interferences = engine.detectInterferences(asm, partsById)
        assertTrue("Two overlapping big parts must produce interferences", interferences.isNotEmpty())
    }

    @Test
    fun `assembly graph rejects cycles`() {
        // Crear grafo i_a → i_b → i_c → i_a via joint cycles.
        var asm = emptyAssembly()
        asm = engine.addPart(asm, "p", "i_a")
        asm = engine.addPart(asm, "p", "i_b")
        asm = engine.addPart(asm, "p", "i_c")
        // Manual: construir un assembly con un ciclo y verificar que hasCycle lo detecta.
        val cyclicAsm = asm.copy(
            joints = listOf(
                MechanicalJoint(id = "j1", jointType = JointType.FIXED, parentInstanceId = "i_a", childInstanceId = "i_b"),
                MechanicalJoint(id = "j2", jointType = JointType.FIXED, parentInstanceId = "i_b", childInstanceId = "i_c"),
                MechanicalJoint(id = "j3", jointType = JointType.FIXED, parentInstanceId = "i_c", childInstanceId = "i_a")
            )
        )
        assertTrue("Cyclic assembly must be detected", engine.hasCycle(cyclicAsm))
        assertFalse("Linear assembly must not be cyclic", engine.hasCycle(asm))
    }

    @Test
    fun `validateAssembly returns safety-critical manual missing issue`() {
        val criticalPart = ForgePart(
            artifact = ForgeArtifact(
                id = "p_critical", name = "Critical", artifactType = ForgeArtifactType.PART,
                safetyClassification = SafetyClassification.SAFETY_CRITICAL_UNCERTIFIED
            ),
            dimensions = DimensionSet(lengthMm = 10.0, widthMm = 10.0, heightMm = 10.0)
        )
        val partsById = mapOf("p_critical" to criticalPart)
        var asm = emptyAssembly()
        asm = engine.addPart(asm, "p_critical", "i_critical")
        val result = engine.validateAssembly(asm, partsById)
        assertTrue("Must flag manual missing for safety-critical part",
            result.issues.any { it.code == ForgeValidationError.MANUAL_MISSING }
        )
    }
}