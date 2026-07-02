package com.elysium.vanguard.forge.domain

import kotlinx.serialization.Serializable

/**
 * Tipos de sistemas que un vehículo puede tener.
 * Cada VehicleSystemNode agrupa un AssemblyNode especializado.
 */
enum class VehicleSystemType {
    CHASSIS, BODY, ENGINE, TRANSMISSION, SUSPENSION,
    BRAKES, STEERING, COOLING, FUEL, EXHAUST,
    ELECTRICAL, INTERIOR, SAFETY
}

/**
 * Nodo de sistema en el árbol de un ForgeVehicle.
 */
@Serializable
data class VehicleSystemNode(
    val id: String,
    val systemType: VehicleSystemType,
    val assemblyId: String,
    val name: String = "",
    val isComplete: Boolean = false,
    val criticalFailureCount: Int = 0
)

/**
 * Tipo de combustible del powertrain.
 */
enum class FuelType { GASOLINE, DIESEL, HYBRID, ELECTRIC, EDUCATIONAL_GENERIC }

/**
 * Punto de la curva torque vs RPM.
 */
@Serializable
data class TorquePoint(
    val rpm: Double,
    val torqueNm: Double
) {
    init {
        require(rpm.isFinite() && rpm >= 0.0) { "rpm must be non-negative finite" }
        require(torqueNm.isFinite() && torqueNm >= 0.0) { "torqueNm must be non-negative finite" }
    }
}

/**
 * Definición completa del powertrain. El motor se encenderá sólo si el assembly
 * referenciado contiene los componentes mínimos (block, crankshaft, pistons, ...).
 */
@Serializable
data class PowertrainDefinition(
    val engineAssemblyId: String,
    val transmissionAssemblyId: String? = null,
    val drivenWheelInstanceIds: List<String> = emptyList(),
    val crankshaftInstanceId: String? = null,
    val pistonInstanceIds: List<String> = emptyList(),
    val camshaftInstanceIds: List<String> = emptyList(),
    val valveInstanceIds: List<String> = emptyList(),
    val ignitionComponentIds: List<String> = emptyList(),
    val fuelComponentIds: List<String> = emptyList(),
    val coolingComponentIds: List<String> = emptyList(),
    val torqueCurve: List<TorquePoint> = emptyList(),
    val idleRpm: Double = 800.0,
    val redlineRpm: Double = 6500.0,
    val displacementCc: Double? = null,
    val cylinderCount: Int? = null,
    val fuelType: FuelType = FuelType.EDUCATIONAL_GENERIC
) {
    init {
        require(idleRpm.isFinite() && idleRpm in 200.0..3000.0) {
            "idleRpm must be in [200, 3000]"
        }
        require(redlineRpm.isFinite() && redlineRpm > idleRpm) {
            "redlineRpm must be finite and > idleRpm"
        }
        displacementCc?.let { require(it > 0.0) { "displacementCc must be > 0" } }
        cylinderCount?.let { require(it in 1..16) { "cylinderCount must be in 1..16" } }
    }

    /** Interpolación lineal de torque por RPM usando la curva; fallback a curva genérica si vacía. */
    fun torqueAt(rpm: Double): Double {
        if (rpm <= 0.0) return 0.0
        if (torqueCurve.isEmpty()) {
            // Curva genérica educativa: pico de torque a 3500 RPM.
            val peakRpm = 3500.0
            val peakTorque = 200.0
            val normalized = 1.0 - kotlin.math.abs(rpm - peakRpm) / peakRpm
            return (peakTorque * normalized.coerceAtLeast(0.0)).coerceAtLeast(0.0)
        }
        val sorted = torqueCurve.sortedBy { it.rpm }
        if (rpm <= sorted.first().rpm) return sorted.first().torqueNm
        if (rpm >= sorted.last().rpm) return sorted.last().torqueNm
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]; val b = sorted[i + 1]
            if (rpm in a.rpm..b.rpm) {
                val t = (rpm - a.rpm) / (b.rpm - a.rpm)
                return a.torqueNm + t * (b.torqueNm - a.torqueNm)
            }
        }
        return sorted.last().torqueNm
    }
}

/**
 * Perfil de diagnóstico del vehículo (qué señales monitorear).
 */
@Serializable
data class DiagnosticProfile(
    val monitoredSignals: List<String> = emptyList(),
    val monitoredDtcCodes: List<String> = emptyList(),
    val reportThrottle: Boolean = true
)

/**
 * Estado de runtime del motor educativo.
 * Actualizado por ForgeEngineSimulator.
 */
@Serializable
data class EngineRuntimeState(
    val ignitionOn: Boolean = false,
    val starterEngaged: Boolean = false,
    val running: Boolean = false,
    val rpm: Double = 0.0,
    val throttle: Double = 0.0,
    val coolantTempC: Double = 25.0,
    val oilPressureKpa: Double = 0.0,
    val batteryVoltage: Double = 12.4,
    val torqueNm: Double = 0.0,
    val misfireDetected: Boolean = false,
    val warnings: List<String> = emptyList()
) {
    init {
        listOf(rpm, throttle, coolantTempC, oilPressureKpa, batteryVoltage, torqueNm).forEach {
            require(it.isFinite()) { "EngineRuntimeState numeric fields must be finite" }
        }
        require(throttle in 0.0..1.0) { "throttle must be in [0,1]" }
    }
}

enum class EngineLifecycleState {
    OFF, IGNITION_ON, CRANKING, IDLE, RUNNING, STALLED, OVERHEATED, FAILED
}

/**
 * Vehículo completo: cabecera + systems + powertrain + escenarios de simulación.
 */
@Serializable
data class ForgeVehicle(
    val artifact: ForgeArtifact,
    val rootAssemblyId: String,
    val systems: List<VehicleSystemNode> = emptyList(),
    val powertrain: PowertrainDefinition? = null,
    val diagnosticProfile: DiagnosticProfile? = null,
    val simulationScenarios: List<String> = emptyList()
) {
    init {
        require(artifact.artifactType == ForgeArtifactType.VEHICLE) {
            "ForgeVehicle.artifact must be of type VEHICLE"
        }
        val types = systems.map { it.systemType }
        require(types.toSet().size == types.size) { "VehicleSystemType must be unique per vehicle" }
    }

    fun completenessPercent(): Double {
        if (systems.isEmpty()) return 0.0
        return 100.0 * systems.count { it.isComplete } / systems.size
    }
}