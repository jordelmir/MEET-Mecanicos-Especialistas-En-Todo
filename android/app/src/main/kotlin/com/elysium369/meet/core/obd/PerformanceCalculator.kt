package com.elysium369.meet.core.obd

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class PerformanceCalculator @Inject constructor() {

    data class PerformanceSnapshot(
        val horsepowerMAF: Float?,
        val horsepowerLoad: Float?,
        val torqueNm: Float?,
        val torqueLbFt: Float?,
        val acceleration: Float?,
        val gForce: Float?,
        val efficiencyPercent: Float?,
        val airFuelRatio: Float?,
        val boostPsiEstimate: Float?
    )

    data class DragStripResult(
        val zeroTo60mph: Float?,
        val zeroTo100kph: Float?,
        val quarterMileTime: Float?,
        val quarterMileSpeed: Float?,
        val topSpeedReached: Float?,
        val peakHp: Float?,
        val peakTorque: Float?,
        val isRunning: Boolean = false
    )

    data class DynoPoint(
        val rpm: Int,
        val hp: Float,
        val torqueNm: Float
    )

    // Configurable vehicle parameters for Dyno calculations
    var vehicleMassKg: Float = 1500f
    var drivetrainLossPercent: Float = 15f

    private var isDynoRunning = false
    private val dynoBins = mutableMapOf<Int, DynoPoint>()

    private var dragStartTimeMs: Long = 0L
    private var isDragRunning = false
    private var time60mph: Float? = null
    private var time100kph: Float? = null
    private var timeQuarterMile: Float? = null
    private var speedAtQuarter: Float? = null
    private var peakHp: Float = 0f
    private var peakTorque: Float = 0f
    private var topSpeed: Float = 0f
    private var lastSpeedKph: Float = 0f
    private var lastSpeedTimestamp: Long = 0L
    private var distanceMeters: Float = 0f
    private var prevSpeedMs: Float = 0f
    private var prevTimestampMs: Long = 0L

    /**
     * Calcula HP/Torque en tiempo real desde datos OBD.
     * MAF-based: HP = MAF_corrected × 0.146 (termodinámica real)
     * Load-based: backup menos preciso.
     */
    fun calculate(liveData: Map<String, Float>): PerformanceSnapshot {
        val rpm = liveData["RPM"] ?: liveData["rpm"]
        val maf = liveData["MAF"] ?: liveData["maf"]
        val load = liveData["LOAD"] ?: liveData["load"] ?: liveData["ENGINE_LOAD"]
        val speed = liveData["SPEED"] ?: liveData["speed"]
        val iat = liveData["IAT"] ?: liveData["iat"] ?: 25f
        val baro = liveData["BARO"] ?: liveData["baro"] ?: 101.325f
        val map = liveData["MAP"] ?: liveData["map"]

        val hpMaf = if (maf != null && maf > 0) {
            val tempFactor = 298f / (273f + iat)
            val pressureFactor = baro / 101.325f
            val correction = (tempFactor * pressureFactor).coerceIn(0.7f, 1.4f)
            (maf * correction * 0.146f)
        } else null

        val hpLoad = if (load != null && rpm != null && rpm > 0) {
            val torqueEst = (load / 100f) * 180f
            (torqueEst * rpm / 9549f) * 1.341f
        } else null

        val bestHp = hpMaf ?: hpLoad
        val torqueNm = if (bestHp != null && rpm != null && rpm > 300) {
            (bestHp * 9549f / (rpm * 1.341f))
        } else null
        val torqueLbFt = torqueNm?.times(0.7376f)

        val now = System.currentTimeMillis()
        val speedMs = (speed ?: 0f) / 3.6f
        val dt = (now - prevTimestampMs) / 1000f
        val accel = if (dt > 0.05f && dt < 2f && prevTimestampMs > 0L) {
            (speedMs - prevSpeedMs) / dt
        } else null
        prevSpeedMs = speedMs
        prevTimestampMs = now

        val volEff = if (maf != null && rpm != null && rpm > 0) {
            val airDensity = 1.225f * (baro / 101.325f) * (293f / (273f + iat))
            val theoretical = (2.0f * rpm * airDensity) / (2f * 60f * 1000f) * 1000f
            if (theoretical > 0) min(maf / theoretical * 100f, 120f) else null
        } else null

        val boostPsi = if (map != null && baro > 0) {
            val boost = map - baro
            if (boost > 0) boost * 0.14504f else null
        } else null

        return PerformanceSnapshot(
            horsepowerMAF = hpMaf,
            horsepowerLoad = hpLoad,
            torqueNm = torqueNm,
            torqueLbFt = torqueLbFt,
            acceleration = accel,
            gForce = accel?.div(9.81f),
            efficiencyPercent = volEff,
            // Prefer real wideband lambda (PID 0144) over load-based estimation
            airFuelRatio = run {
                val lambda = liveData["Ratio Aire/Comb"] ?: liveData["EQUIVALENCE_RATIO"]
                if (lambda != null && lambda > 0f) {
                    lambda * 14.7f  // λ × stoichiometric = real AFR
                } else if (load != null) {
                    14.7f * (1f + (load - 50f) * 0.003f) // fallback estimate
                } else null
            },
            boostPsiEstimate = boostPsi
        )
    }

    fun startDragRun() {
        isDragRunning = true
        dragStartTimeMs = System.currentTimeMillis()
        time60mph = null; time100kph = null; timeQuarterMile = null
        speedAtQuarter = null; peakHp = 0f; peakTorque = 0f
        topSpeed = 0f; distanceMeters = 0f; lastSpeedKph = 0f
        lastSpeedTimestamp = dragStartTimeMs
    }

    fun stopDragRun(): DragStripResult {
        isDragRunning = false
        return DragStripResult(time60mph, time100kph, timeQuarterMile,
            speedAtQuarter, topSpeed,
            if (peakHp > 0) peakHp else null,
            if (peakTorque > 0) peakTorque else null, false)
    }

    fun updateDragRun(speedKph: Float, hp: Float?, torqueNm: Float?): DragStripResult {
        if (!isDragRunning) return DragStripResult(null, null, null, null, null, null, null, false)
        val now = System.currentTimeMillis()
        val elapsed = (now - dragStartTimeMs) / 1000f
        val dt = (now - lastSpeedTimestamp) / 1000f
        if (dt > 0 && dt < 2f) distanceMeters += ((lastSpeedKph + speedKph) / 2f / 3.6f) * dt
        if (time60mph == null && speedKph * 0.621371f >= 60f) time60mph = elapsed
        if (time100kph == null && speedKph >= 100f) time100kph = elapsed
        if (timeQuarterMile == null && distanceMeters >= 402.336f) {
            timeQuarterMile = elapsed; speedAtQuarter = speedKph
        }
        if (hp != null && hp > peakHp) peakHp = hp
        if (torqueNm != null && torqueNm > peakTorque) peakTorque = torqueNm
        if (speedKph > topSpeed) topSpeed = speedKph
        lastSpeedKph = speedKph; lastSpeedTimestamp = now
        return DragStripResult(time60mph, time100kph, timeQuarterMile, speedAtQuarter,
            topSpeed, if (peakHp > 0) peakHp else null,
            if (peakTorque > 0) peakTorque else null, true)
    }

    val isDragActive: Boolean get() = isDragRunning

    fun startDynoRun() {
        isDynoRunning = true
        dynoBins.clear()
    }

    fun stopDynoRun(): List<DynoPoint> {
        isDynoRunning = false
        return getDynoPoints()
    }

    fun getDynoPoints(): List<DynoPoint> {
        return dynoBins.values.sortedBy { it.rpm }
    }

    fun updateDynoRun(rpm: Float, accelMs2: Float, speedKph: Float): List<DynoPoint> {
        if (!isDynoRunning || rpm < 800 || speedKph < 5f) return getDynoPoints()

        // 1. Calculate HP and Torque using weight-based physics
        val speedMs = speedKph / 3.6f
        
        // Power = Force * Velocity
        // Force = Mass * Acceleration
        val force = vehicleMassKg * accelMs2
        val powerWheelWatts = force * speedMs
        
        // Engine Power corrected for drivetrain loss
        val lossFactor = drivetrainLossPercent / 100f
        val powerEngineWatts = powerWheelWatts / (1f - lossFactor)
        
        // Convert to HP (745.7 Watts = 1 HP)
        val hp = (powerEngineWatts / 745.7f).coerceAtLeast(0f)
        
        // Torque (Nm) = (HP * 9549) / (RPM * 1.341)
        val torqueNm = if (rpm > 300) {
            (hp * 9549f / (rpm * 1.341f)).coerceAtLeast(0f)
        } else 0f

        // 2. Map to RPM bin (bin of 200 RPM: 2000, 2200, 2400...)
        val binSize = 200
        val rpmBin = ((rpm + binSize / 2).toInt() / binSize) * binSize
        
        if (rpmBin in 1000..8500) {
            val existing = dynoBins[rpmBin]
            if (existing == null || hp > existing.hp) {
                dynoBins[rpmBin] = DynoPoint(rpmBin, hp, torqueNm)
            }
        }
        return getDynoPoints()
    }

    val isDynoActive: Boolean get() = isDynoRunning
}
