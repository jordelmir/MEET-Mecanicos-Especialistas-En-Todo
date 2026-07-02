package com.elysium.vanguard.forge.diagnostics

import com.elysium.vanguard.forge.domain.DamageSeverity
import com.elysium.vanguard.forge.domain.DamageState
import com.elysium.vanguard.forge.domain.DamageType
import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.SafetyClassification
import com.elysium.vanguard.forge.domain.TransformData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeDamageEngineTest {

    private val engine = ForgeDamageEngine()

    private fun instance(state: DamageState = DamageState()): com.elysium.vanguard.forge.domain.PartInstance {
        return com.elysium.vanguard.forge.domain.PartInstance(
            id = "i1", partId = "p1",
            transform = TransformData(),
            damageState = state
        )
    }

    @Test
    fun `apply damage reduces health and updates state`() {
        val updated = engine.applyDamage(instance(), DamageType.WEAR, DamageSeverity.MEDIUM)
        assertEquals(60.0, updated.damageState.healthPercent, 0.001)
        assertTrue(DamageType.WEAR in updated.damageState.damageTypes)
        assertEquals(DamageSeverity.MEDIUM, updated.damageState.severity)
    }

    @Test
    fun `repair damage restores health and clears types`() {
        val damaged = engine.applyDamage(instance(), DamageType.WEAR, DamageSeverity.HIGH)
        val repaired = engine.repairDamage(damaged)
        assertTrue(repaired.damageState.damageTypes.isEmpty())
        assertEquals(90.0, repaired.damageState.healthPercent, 0.001)
        assertEquals(DamageSeverity.NONE, repaired.damageState.severity)
    }

    @Test
    fun `replace part resets damage completely`() {
        val damaged = engine.applyDamage(instance(), DamageType.BROKEN, DamageSeverity.CRITICAL)
        val replaced = engine.replacePart(damaged)
        assertEquals(DamageState(), replaced.damageState)
    }

    @Test
    fun `seized part produces overlay with high severity`() {
        val seized = engine.applyDamage(instance(), DamageType.SEIZED, DamageSeverity.HIGH)
        val overlay = engine.getVisualDamageOverlay(seized)
        assertEquals(DamageSeverity.HIGH, overlay.severity)
    }

    @Test
    fun `leak creates visual overlay`() {
        val leaking = engine.applyDamage(instance(), DamageType.LEAK, DamageSeverity.MEDIUM)
        val overlay = engine.getVisualDamageOverlay(leaking)
        assertTrue(overlay.showLeak)
    }

    @Test
    fun `compute damage effects aggregates torque loss`() {
        val asm = ForgeAssembly(
            artifact = ForgeArtifact(
                id = "asm", name = "Asm", artifactType = ForgeArtifactType.ASSEMBLY,
                safetyClassification = SafetyClassification.EDUCATIONAL
            ),
            instances = listOf(
                engine.applyDamage(
                    instance().copy(id = "i1"),
                    DamageType.BROKEN,
                    DamageSeverity.HIGH
                ),
                engine.applyDamage(
                    instance().copy(id = "i2"),
                    DamageType.WEAR,
                    DamageSeverity.MEDIUM
                )
            )
        )
        val effects = engine.computeDamageEffects(asm)
        assertTrue("Total torque loss must be > 0", effects.totalTorqueLossPercent > 0.0)
        assertEquals(2, effects.affectedInstanceIds.size)
    }
}