package com.elysium.vanguard.forge.physics

import com.elysium.vanguard.forge.domain.DamageSeverity
import com.elysium.vanguard.forge.domain.DamageState
import com.elysium.vanguard.forge.domain.EngineLifecycleState
import com.elysium.vanguard.forge.domain.EngineRuntimeSnapshot
import com.elysium.vanguard.forge.domain.EngineRuntimeState
import com.elysium.vanguard.forge.domain.EngineStartValidation
import com.elysium.vanguard.forge.domain.FailureDetection
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.JointMotionCommand
import com.elysium.vanguard.forge.domain.JointType
import com.elysium.vanguard.forge.domain.PowertrainDefinition
import com.elysium.vanguard.forge.domain.Vector3Data
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ForgeEngineSimulator V1.
 *
 * Simula el ciclo de un motor educativo:
 * OFF → IGNITION_ON → CRANKING → IDLE → RUNNING → (STALLED | OVERHEATED | FAILED)
 *
 * No pretende ser un modelo termodinámico real — usa cinemática determinística
 * + reglas heurísticas (pérdida de compresión, sobrecalentamiento por tiempo en idle).
 *
 * Mapeo:
 * - crankshaftAngleDeg avanza en función de rpm.
 * - pistonPositionsMm siguen la carrera: x = stroke * sin(angle) + stroke/2.
 * - valves: apertura cuando cos(angle) > 0 (heurística 4 tiempos).
 */
class ForgeEngineSimulator(
    private val physics: ForgePhysicsEngine
) {

    private var lifecycle: EngineLifecycleState = EngineLifecycleState.OFF
    private var runtime: EngineRuntimeState = EngineRuntimeState()
    private var crankshaftAngleDeg: Double = 0.0
    private var elapsedCrankingSec: Double = 0.0
    private var idleElapsedSec: Double = 0.0

    fun canStart(
        powertrain: PowertrainDefinition,
        assembly: ForgeAssembly,
        partsById: Map<String, com.elysium.vanguard.forge.domain.ForgePart> = emptyMap()
    ): EngineStartValidation {
        val missing = mutableListOf<String>()
        val damaged = mutableListOf<String>()

        // Verificar instancias críticas existen.
        if (powertrain.crankshaftInstanceId == null ||
            assembly.instances.none { it.id == powertrain.crankshaftInstanceId }) {
            missing += "cigüeñal"
        }
        if (powertrain.pistonInstanceIds.isEmpty() ||
            powertrain.pistonInstanceIds.none { id -> assembly.instances.any { it.id == id } }) {
            missing += "pistones"
        }
        if (powertrain.ignitionComponentIds.isEmpty() ||
            powertrain.ignitionComponentIds.none { id -> assembly.instances.any { it.id == id } }) {
            missing += "ignición (bujías)"
        }
        if (powertrain.fuelComponentIds.isEmpty() ||
            powertrain.fuelComponentIds.none { id -> assembly.instances.any { it.id == id } }) {
            missing += "combustible (inyectores)"
        }

        // Verificar daños severos en componentes críticos.
        for (instance in assembly.instances) {
            val ds = instance.damageState
            if (ds.severity >= DamageSeverity.HIGH && instance.id in (
                listOfNotNull(powertrain.crankshaftInstanceId) +
                powertrain.pistonInstanceIds +
                powertrain.ignitionComponentIds +
                powertrain.fuelComponentIds +
                powertrain.coolingComponentIds
            )) {
                damaged += instance.id
            }
        }

        return if (missing.isEmpty() && damaged.isEmpty()) EngineStartValidation.ok()
        else EngineStartValidation.blocked(missing, damaged)
    }

    /**
     * Intenta arrancar el motor. Devuelve el snapshot inicial si OK, o el estado de bloqueo.
     */
    fun startEngine(powertrain: PowertrainDefinition, assembly: ForgeAssembly): EngineStartValidation {
        val validation = canStart(powertrain, assembly)
        if (!validation.canStart) {
            lifecycle = EngineLifecycleState.FAILED
            runtime = runtime.copy(
                running = false,
                rpm = 0.0,
                warnings = listOf(validation.message)
            )
            return validation
        }
        lifecycle = EngineLifecycleState.IGNITION_ON
        runtime = runtime.copy(
            ignitionOn = true,
            batteryVoltage = 12.4,
            oilPressureKpa = 0.0,
            warnings = emptyList()
        )
        return validation
    }

    fun stopEngine() {
        lifecycle = EngineLifecycleState.OFF
        runtime = runtime.copy(
            ignitionOn = false,
            starterEngaged = false,
            running = false,
            rpm = 0.0,
            torqueNm = 0.0,
            warnings = emptyList()
        )
    }

    fun updateThrottle(value: Double) {
        val clamped = if (value.isFinite()) value.coerceIn(0.0, 1.0) else 0.0
        runtime = runtime.copy(throttle = clamped)
    }

    /**
     * Avanza la simulación. Llamar periódicamente (deltaTimeSec ~ 0.016-0.05).
     */
    fun stepEngine(deltaTimeSec: Double, assembly: ForgeAssembly): EngineRuntimeSnapshot {
        val dt = if (deltaTimeSec.isFinite() && deltaTimeSec > 0.0) {
            min(deltaTimeSec, 0.1)
        } else 1.0 / 60.0

        // Lógica de transición de estado.
        when (lifecycle) {
            EngineLifecycleState.OFF -> {
                // En OFF no hace nada.
            }
            EngineLifecycleState.IGNITION_ON -> {
                if (runtime.starterEngaged) {
                    lifecycle = EngineLifecycleState.CRANKING
                    elapsedCrankingSec = 0.0
                }
            }
            EngineLifecycleState.CRANKING -> {
                elapsedCrankingSec += dt
                // Arrancar cuando rpm > 200 con ignición.
                if (runtime.rpm > 200.0 && runtime.ignitionOn) {
                    lifecycle = EngineLifecycleState.IDLE
                    runtime = runtime.copy(running = true)
                }
                // Si cranking > 8 segundos sin éxito → stalled.
                if (elapsedCrankingSec > 8.0) {
                    lifecycle = EngineLifecycleState.STALLED
                    runtime = runtime.copy(
                        running = false,
                        rpm = 0.0,
                        warnings = listOf("Cranking prolongado sin encendido")
                    )
                }
            }
            EngineLifecycleState.IDLE, EngineLifecycleState.RUNNING -> {
                // Si throttle > 0.1, considerar RUNNING.
                if (runtime.throttle > 0.1) lifecycle = EngineLifecycleState.RUNNING
                idleElapsedSec += dt
            }
            EngineLifecycleState.STALLED -> { /* espera STOP */ }
            EngineLifecycleState.OVERHEATED -> { /* puede volver a RUNNING si temp baja */ }
            EngineLifecycleState.FAILED -> { /* requiere reset */ }
        }

        // Integración RPM según estado.
        runtime = when (lifecycle) {
            EngineLifecycleState.CRANKING -> runtime.copy(
                rpm = (runtime.rpm + (180.0 - runtime.rpm) * 5.0 * dt).coerceAtMost(250.0),
                torqueNm = 20.0,
                batteryVoltage = (runtime.batteryVoltage - 0.05).coerceAtLeast(10.0)
            )
            EngineLifecycleState.IDLE -> {
                val idleRpm = 800.0
                val next = runtime.rpm + (idleRpm - runtime.rpm) * 2.0 * dt
                runtime.copy(
                    rpm = clampFinite(next, 0.0, idleRpm + 100.0),
                    torqueNm = powertrainTorqueAt(runtime.throttle, runtime.rpm),
                    oilPressureKpa = 250.0,
                    batteryVoltage = 13.8
                )
            }
            EngineLifecycleState.RUNNING -> {
                val maxRpm = 6500.0
                val targetRpm = 1000.0 + runtime.throttle * 5500.0
                val next = runtime.rpm + (targetRpm - runtime.rpm) * 3.0 * dt
                runtime.copy(
                    rpm = clampFinite(next, 0.0, maxRpm),
                    torqueNm = powertrainTorqueAt(runtime.throttle, runtime.rpm),
                    oilPressureKpa = 300.0,
                    batteryVoltage = 14.2
                )
            }
            EngineLifecycleState.STALLED -> runtime.copy(rpm = 0.0, running = false, torqueNm = 0.0)
            EngineLifecycleState.OVERHEATED -> runtime.copy(
                rpm = (runtime.rpm - 300.0 * dt).coerceAtLeast(0.0),
                warnings = runtime.warnings + "Motor sobrecalentado"
            )
            EngineLifecycleState.FAILED -> runtime.copy(rpm = 0.0, running = false, torqueNm = 0.0)
            EngineLifecycleState.OFF, EngineLifecycleState.IGNITION_ON -> runtime
        }

        // Termostato: temp sube cuando running, baja cuando off.
        runtime = if (runtime.running) {
            val targetTemp = 90.0
            val next = runtime.coolantTempC + (targetTemp - runtime.coolantTempC) * 0.02 * dt
            runtime.copy(coolantTempC = clampFinite(next, 25.0, 130.0))
        } else {
            val ambient = 25.0
            val next = runtime.coolantTempC + (ambient - runtime.coolantTempC) * 0.01 * dt
            runtime.copy(coolantTempC = clampFinite(next, 25.0, 130.0))
        }

        // Detectar fallos runtime.
        val failures = detectRuntimeFailures(runtime, assembly)

        // Aplicar motor torque al cigüeñal.
        crankshaftAngleDeg = (crankshaftAngleDeg + runtime.rpm / 60.0 * 360.0 * dt).mod(360.0)

        // Mapeo a comandos de joints.
        val motionCommands = mapEngineMotionToAssembly(runtime, assembly)

        // Detectar sobrecalentamiento (transición).
        if (runtime.coolantTempC > 110.0 && lifecycle == EngineLifecycleState.RUNNING) {
            lifecycle = EngineLifecycleState.OVERHEATED
            runtime = runtime.copy(warnings = runtime.warnings + "Temperatura crítica")
        }
        if (runtime.coolantTempC < 95.0 && lifecycle == EngineLifecycleState.OVERHEATED) {
            lifecycle = EngineLifecycleState.IDLE
        }

        return EngineRuntimeSnapshot(
            state = runtime,
            lifecycle = lifecycle,
            warnings = runtime.warnings + failures.map { it.warning },
            detectedFailures = failures.map { it.title },
            crankshaftAngleDeg = crankshaftAngleDeg,
            pistonPositionsMm = computePistonPositions(assembly, crankshaftAngleDeg)
        )
    }

    /**
     * Inicia el cranking explícitamente. Llamar después de startEngine() para arrancar.
     */
    fun engageStarter() {
        if (lifecycle == EngineLifecycleState.IGNITION_ON) {
            runtime = runtime.copy(starterEngaged = true)
        }
    }

    fun disengageStarter() {
        runtime = runtime.copy(starterEngaged = false)
    }

    fun reset() {
        lifecycle = EngineLifecycleState.OFF
        runtime = EngineRuntimeState()
        crankshaftAngleDeg = 0.0
        elapsedCrankingSec = 0.0
        idleElapsedSec = 0.0
    }

    fun detectRuntimeFailures(state: EngineRuntimeState, assembly: ForgeAssembly): List<FailureDetection> {
        val failures = mutableListOf<FailureDetection>()
        // Misfire si spark plug dañada.
        for (instance in assembly.instances) {
            val ds = instance.damageState
            val isSparkRelated = instance.id.contains("spark", ignoreCase = true) ||
                instance.partId.contains("spark", ignoreCase = true) ||
                instance.partId.contains("buji", ignoreCase = true) ||
                instance.partId.contains("ignition", ignoreCase = true)
            if (ds.damageTypes.contains(com.elysium.vanguard.forge.domain.DamageType.ELECTRICAL_OPEN) &&
                isSparkRelated
            ) {
                failures += FailureDetection(
                    id = "fd_misfire_${instance.id}",
                    componentInstanceId = instance.id,
                    componentName = instance.partId,
                    title = "Misfare detectado (misfire)",
                    severity = ds.severity,
                    warning = "Bujía con circuito abierto — pérdida de chispa."
                )
            }
            if (ds.damageTypes.contains(com.elysium.vanguard.forge.domain.DamageType.BROKEN) &&
                instance.id.contains("belt", ignoreCase = true)
            ) {
                failures += FailureDetection(
                    id = "fd_belt_${instance.id}",
                    componentInstanceId = instance.id,
                    componentName = instance.partId,
                    title = "Correa rota",
                    severity = ds.severity,
                    warning = "Correa rota — accesorios no giran."
                )
            }
            if (ds.damageTypes.contains(com.elysium.vanguard.forge.domain.DamageType.LEAK) &&
                instance.id.contains("water_pump", ignoreCase = true)
            ) {
                failures += FailureDetection(
                    id = "fd_leak_${instance.id}",
                    componentInstanceId = instance.id,
                    componentName = instance.partId,
                    title = "Fuga en bomba de agua",
                    severity = ds.severity,
                    warning = "Fuga detectada — riesgo de sobrecalentamiento."
                )
            }
            if (ds.damageTypes.contains(com.elysium.vanguard.forge.domain.DamageType.SEIZED)) {
                failures += FailureDetection(
                    id = "fd_seized_${instance.id}",
                    componentInstanceId = instance.id,
                    componentName = instance.partId,
                    title = "Pieza agarrotada",
                    severity = DamageSeverity.CRITICAL,
                    warning = "Pieza agarrotada — motor podría detenerse."
                )
            }
        }
        if (state.coolantTempC > 110.0) {
            failures += FailureDetection(
                id = "fd_overheat",
                componentInstanceId = "engine_thermal",
                componentName = "Sistema de enfriamiento",
                title = "Sobrecalentamiento",
                severity = DamageSeverity.HIGH,
                warning = "Temperatura del refrigerante ${"%.1f".format(state.coolantTempC)}°C — por encima del rango seguro."
            )
        }
        if (state.oilPressureKpa in 1.0..150.0 && state.running) {
            failures += FailureDetection(
                id = "fd_low_oil",
                componentInstanceId = "engine_oil",
                componentName = "Sistema de lubricación",
                title = "Presión de aceite baja",
                severity = DamageSeverity.MEDIUM,
                warning = "Presión de aceite ${"%.0f".format(state.oilPressureKpa)} kPa — rango seguro > 150 kPa."
            )
        }
        return failures
    }

    fun mapEngineMotionToAssembly(state: EngineRuntimeState, assembly: ForgeAssembly): List<JointMotionCommand> {
        if (!state.running || state.rpm <= 0.0) return emptyList()
        val rpm = state.rpm.coerceAtLeast(0.0)
        val crankshaftRate = rpm / 60.0 * 360.0 // deg/sec
        val cmds = mutableListOf<JointMotionCommand>()
        for (joint in assembly.joints) {
            when {
                joint.name.contains("crank", ignoreCase = true) ||
                joint.jointType == JointType.REVOLUTE && joint.parentInstanceId.contains("block", ignoreCase = true) -> {
                    cmds += JointMotionCommand(
                        jointId = joint.id,
                        targetValue = 0.0,
                        velocity = crankshaftRate
                    )
                }
                joint.jointType == JointType.BELT -> {
                    cmds += JointMotionCommand(
                        jointId = joint.id,
                        targetValue = 0.0,
                        velocity = crankshaftRate * 0.5
                    )
                }
            }
        }
        return cmds
    }

    // --- Internos ---

    private fun powertrainTorqueAt(throttle: Double, rpm: Double): Double {
        // Curva simplificada: pico cerca de 3500 RPM.
        val peakRpm = 3500.0
        val peakTorque = 200.0
        val normalized = 1.0 - abs(rpm - peakRpm) / peakRpm
        return peakTorque * normalized.coerceAtLeast(0.0) * throttle
    }

    private fun computePistonPositions(assembly: ForgeAssembly, angleDeg: Double): Map<String, Double> {
        val positions = HashMap<String, Double>()
        val strokeMm = 80.0 // carrera genérica educativa
        val angleRad = angleDeg * PI / 180.0
        // Pistones en fase: 1 y 4 juntos, 2 y 3 juntos (4 cilindros).
        val phases = doubleArrayOf(0.0, Math.PI, Math.PI, 0.0)
        var idx = 0
        for (instance in assembly.instances) {
            if (!instance.partId.contains("piston", ignoreCase = true)) continue
            val phase = phases[idx % phases.size]
            val position = (strokeMm / 2.0) + (strokeMm / 2.0) * kotlin.math.sin(angleRad + phase)
            positions[instance.id] = position
            idx++
        }
        return positions
    }

    private fun clampFinite(value: Double, min: Double, max: Double): Double {
        if (!value.isFinite()) return 0.0
        return value.coerceIn(min, max)
    }
}