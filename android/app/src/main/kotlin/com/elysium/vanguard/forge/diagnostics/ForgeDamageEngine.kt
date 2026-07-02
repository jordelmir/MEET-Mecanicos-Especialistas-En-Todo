package com.elysium.vanguard.forge.diagnostics

import com.elysium.vanguard.forge.domain.DamageState
import com.elysium.vanguard.forge.domain.DamageSeverity
import com.elysium.vanguard.forge.domain.DamageType
import com.elysium.vanguard.forge.domain.DamageVisualOverlay
import com.elysium.vanguard.forge.domain.DamageEffects
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.PartInstance

/**
 * ForgeDamageEngine V1.
 *
 * Aplica, repara y reemplaza daño sobre PartInstances.
 *
 * No es persistente — opera sobre los DamageState del assembly.
 * El "reemplazo" sólo resetea el estado; no intercambia la pieza por otra versión.
 */
class ForgeDamageEngine {

    fun applyDamage(
        instance: PartInstance,
        damageType: DamageType,
        severity: DamageSeverity
    ): PartInstance {
        val newTypes = if (instance.damageState.damageTypes.contains(damageType)) {
            instance.damageState.damageTypes
        } else {
            instance.damageState.damageTypes + damageType
        }
        val newSeverity = maxOf(instance.damageState.severity, severity)
        val newHealth = when (severity) {
            DamageSeverity.NONE -> instance.damageState.healthPercent
            DamageSeverity.LOW -> minOf(instance.damageState.healthPercent, 85.0)
            DamageSeverity.MEDIUM -> minOf(instance.damageState.healthPercent, 60.0)
            DamageSeverity.HIGH -> minOf(instance.damageState.healthPercent, 30.0)
            DamageSeverity.CRITICAL -> 0.0
        }
        val failed = severity == DamageSeverity.CRITICAL
        return instance.copy(
            damageState = DamageState(
                healthPercent = newHealth,
                damageTypes = newTypes,
                severity = newSeverity,
                isFailed = failed || instance.damageState.isFailed
            )
        )
    }

    fun repairDamage(instance: PartInstance): PartInstance {
        return instance.copy(
            damageState = DamageState(
                healthPercent = maxOf(instance.damageState.healthPercent, 90.0),
                damageTypes = emptyList(),
                severity = DamageSeverity.NONE,
                isFailed = false
            )
        )
    }

    fun replacePart(instance: PartInstance): PartInstance {
        return instance.copy(
            damageState = DamageState()
        )
    }

    /**
     * Recalcula efectos agregados del daño en el assembly.
     */
    fun computeDamageEffects(assembly: ForgeAssembly): DamageEffects {
        var torqueLoss = 0.0
        var friction = 0.0
        var vibration = 0.0
        var tempRise = 0.0
        val affected = mutableListOf<String>()
        for (instance in assembly.instances) {
            val ds = instance.damageState
            if (ds.damageTypes.isEmpty()) continue
            affected += instance.id
            val severityFactor = ds.severity.ordinal.toDouble() / DamageSeverity.CRITICAL.ordinal.toDouble()
            if (ds.damageTypes.contains(DamageType.WEAR)) {
                friction += 0.05 * severityFactor
            }
            if (ds.damageTypes.contains(DamageType.SEIZED) || ds.damageTypes.contains(DamageType.BROKEN)) {
                torqueLoss += 25.0 * severityFactor
            }
            if (ds.damageTypes.contains(DamageType.CRACK) || ds.damageTypes.contains(DamageType.MISALIGNED)) {
                vibration += 0.1 * severityFactor
            }
            if (ds.damageTypes.contains(DamageType.OVERHEATED) || ds.damageTypes.contains(DamageType.LEAK)) {
                tempRise += 5.0 * severityFactor
            }
            if (ds.damageTypes.contains(DamageType.CLOGGED)) {
                torqueLoss += 15.0 * severityFactor
            }
        }
        return DamageEffects(
            assemblyId = assembly.artifact.id,
            totalTorqueLossPercent = torqueLoss.coerceAtMost(100.0),
            additionalFriction = friction,
            additionalVibration = vibration,
            temperatureRiseC = tempRise,
            affectedInstanceIds = affected
        )
    }

    /**
     * Genera overlay visual para aplicar al renderer.
     */
    fun getVisualDamageOverlay(instance: PartInstance): DamageVisualOverlay {
        val ds = instance.damageState
        val severityColor = when (ds.severity) {
            DamageSeverity.NONE -> 0xFF00FF00.toLong() // verde
            DamageSeverity.LOW -> 0xFFFFFF00.toLong()  // amarillo
            DamageSeverity.MEDIUM -> 0xFFFF8800.toLong() // naranja
            DamageSeverity.HIGH -> 0xFFFF2200.toLong() // rojo
            DamageSeverity.CRITICAL -> 0xFFAA0000.toLong() // rojo oscuro
        }
        return DamageVisualOverlay(
            instanceId = instance.id,
            severity = ds.severity,
            showCrack = ds.damageTypes.contains(DamageType.CRACK) || ds.damageTypes.contains(DamageType.BROKEN),
            showLeak = ds.damageTypes.contains(DamageType.LEAK),
            colorOverlay = severityColor,
            vibrateHz = if (ds.damageTypes.contains(DamageType.LOOSE) || ds.damageTypes.contains(DamageType.MISALIGNED)) 8.0 else 0.0
        )
    }
}