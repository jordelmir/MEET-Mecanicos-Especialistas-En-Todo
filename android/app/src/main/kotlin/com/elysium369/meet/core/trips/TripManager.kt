package com.elysium369.meet.core.trips

import com.elysium369.meet.core.obd.ObdSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class Trip(
    val id: String,
    val vehicleId: String,
    val startTime: Long,
    var endTime: Long? = null,
    var durationMs: Long = 0,
    var distanceKm: Float = 0f,
    var maxSpeed: Float = 0f,
    var avgSpeed: Float = 0f,
    var maxRpm: Float = 0f,
    var avgRpm: Float = 0f,
    var maxTemp: Float = 0f,
    var fuelConsumedLiters: Float = 0f,
    var ecoScore: Int = 100
)

@Singleton
class TripManager @Inject constructor(
    private val obdSession: ObdSession,
    private val scope: CoroutineScope
) {
    private var currentTrip: Trip? = null
    private var job: Job? = null
    
    // Acumuladores para promedios
    private var speedSum = 0f
    private var speedCount = 0
    private var rpmSum = 0f
    private var rpmCount = 0

    private val speedReadings = mutableListOf<Float>()
    private val rpmReadings = mutableListOf<Float>()
    private val throttleReadings = mutableListOf<Float>()

    fun startMonitoring(vehicleId: String) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            // In a real implementation we would collect from the liveData map
            // Assuming ObdSession exposes a way to observe PIDs
            // obdSession.liveData.collectLatest { data -> processLiveData(data) }
        }
    }

    fun processLiveData(data: Map<String, Float>, vehicleId: String) {
        val speed = data["010D"] ?: 0f
        
        // Detectar inicio de viaje: velocidad > 5 km/h
        if (currentTrip == null && speed > 5f) {
            currentTrip = Trip(
                id = java.util.UUID.randomUUID().toString(),
                vehicleId = vehicleId,
                startTime = System.currentTimeMillis()
            )
            speedReadings.clear()
            rpmReadings.clear()
            throttleReadings.clear()
        }

        currentTrip?.let { trip ->
            // Actualizar máximos
            trip.maxSpeed = maxOf(trip.maxSpeed, speed)
            
            val rpm = data["010C"] ?: 0f
            trip.maxRpm = maxOf(trip.maxRpm, rpm)
            
            val temp = data["0105"] ?: 0f
            trip.maxTemp = maxOf(trip.maxTemp, temp)
            
            val throttle = data["0111"] ?: 0f

            // Acumular promedios y lecturas
            if (speed > 0) {
                speedSum += speed
                speedCount++
                trip.avgSpeed = speedSum / speedCount
                speedReadings.add(speed)
            }
            if (rpm > 0) {
                rpmSum += rpm
                rpmCount++
                trip.avgRpm = rpmSum / rpmCount
                rpmReadings.add(rpm)
            }
            if (throttle > 0) {
                throttleReadings.add(throttle)
            }

            // Lógica de eco-score real
            if (speedReadings.isNotEmpty() && rpmReadings.isNotEmpty()) {
                trip.ecoScore = calculateEcoScore(speedReadings, rpmReadings, throttleReadings)
            }
        }
    }

    private fun calculateEcoScore(
        speedReadings: List<Float>,
        rpmReadings: List<Float>,
        throttleReadings: List<Float>
    ): Int {
        var score = 100
        // Penalizar aceleraciones bruscas (cambio de velocidad > 10 km/h en 2s)
        val harshAccelerations = speedReadings.zipWithNext()
            .count { (a, b) -> (b - a) > 10f }
        score -= harshAccelerations * 3
        
        // Penalizar RPM alto sostenido (>3500 por más del 20% del tiempo)
        val highRpmPct = rpmReadings.count { it > 3500f }.toFloat() / rpmReadings.size
        if (highRpmPct > 0.2f) score -= ((highRpmPct - 0.2f) * 100).toInt() * 2
        
        // Penalizar aceleraciones a fondo (throttle > 80%)
        val hardThrottle = throttleReadings.count { it > 80f }
        score -= hardThrottle * 2
        
        return score.coerceIn(0, 100)
    }

    fun endTrip(): Trip? {
        val trip = currentTrip?.apply {
            endTime = System.currentTimeMillis()
            durationMs = endTime!! - startTime
        }
        currentTrip = null
        speedSum = 0f
        speedCount = 0
        rpmSum = 0f
        rpmCount = 0
        speedReadings.clear()
        rpmReadings.clear()
        throttleReadings.clear()
        return trip
    }
}
