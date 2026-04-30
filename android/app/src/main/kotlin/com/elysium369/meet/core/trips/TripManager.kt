package com.elysium369.meet.core.trips

import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.data.local.dao.TripDao
import com.elysium369.meet.data.local.entities.TripEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

/**
 * TripManager — Professional trip tracking and telemetry analysis engine.
 * Calculates distance, fuel consumption, and driving behavior (EcoScore) in real-time.
 */
@Singleton
class TripManager @Inject constructor(
    private val obdSession: ObdSession,
    private val tripRepository: com.elysium369.meet.data.supabase.TripRepository,
    private val scope: CoroutineScope
) {
    private var _currentTrip: TripEntity? = null
    val currentTrip: TripEntity? get() = _currentTrip
    
    private var monitoringJob: Job? = null
    
    // Telemetry Accumulators
    private var lastTimestamp: Long = 0
    private var speedSum = 0f
    private var speedCount = 0
    private var rpmSum = 0f
    private var rpmCount = 0
    private var totalDistance = 0f // Km
    private var totalFuelConsumed = 0f // Liters (Estimated)
    
    private var fuelCalibrationFactor = 1.0f // Multiplier for fine-tuning
    
    private val speedHistory = mutableListOf<Float>()
    private val rpmHistory = mutableListOf<Float>()
    private val throttleHistory = mutableListOf<Float>()

    fun startMonitoring(vehicleId: String, sessionId: String) {
        monitoringJob?.cancel()
        resetAccumulators()
        
        _currentTrip = TripEntity(
            id = UUID.randomUUID().toString(),
            vehicleId = vehicleId,
            sessionId = sessionId,
            startedAt = System.currentTimeMillis(),
            endedAt = null,
            distanceKm = 0f,
            durationSeconds = 0,
            avgSpeedKmh = 0f,
            maxSpeedKmh = 0f,
            maxRpm = 0f,
            avgRpm = 0f,
            maxTempC = 0f,
            fuelEfficiency = null,
            ecoScore = 100,
            gpsTrackJson = null,
            synced = false
        )

        monitoringJob = scope.launch(Dispatchers.IO) {
            obdSession.liveData.collectLatest { data ->
                updateTelemetry(data)
            }
        }
    }

    private fun updateTelemetry(data: Map<String, Float>) {
        val trip = _currentTrip ?: return
        val now = System.currentTimeMillis()
        
        // 1. Precise Distance Calculation (Integration)
        val currentSpeed = data["010D"] ?: 0f // Km/h
        val deltaTimeMillis = if (lastTimestamp > 0) (now - lastTimestamp) else 0L
        val deltaTimeHours = deltaTimeMillis / 3600000f
        
        if (deltaTimeMillis > 0) {
            totalDistance += currentSpeed * deltaTimeHours
        }

        // 2. High-Fidelity Fuel Consumption Estimation (Professional Grade)
        // Order of precedence: Direct Fuel Rate > MAF + Fuel Trims > MAF Base
        val fuelRate = data["015E"] // L/h
        if (fuelRate != null) {
            totalFuelConsumed += fuelRate * deltaTimeHours
        } else {
            val maf = data["0110"] // g/s
            if (maf != null) {
                // Apply Fuel Trims (STFT + LTFT) for precise air-fuel ratio correction
                val stft = data["0106"] ?: 0f // Short Term Fuel Trim Bank 1 (%)
                val ltft = data["0107"] ?: 0f // Long Term Fuel Trim Bank 1 (%)
                val totalTrimMultiplier = 1.0f + ((stft + ltft) / 100f)

                // Stoichiometric ratio 14.7:1 for gasoline
                // Gasoline density ~ 740 g/L (0.74 kg/L)
                val baseFuelGps = maf / 14.7f
                val correctedFuelGps = baseFuelGps * totalTrimMultiplier
                
                val litersPerSecond = correctedFuelGps / 740f
                val deltaTimeSeconds = deltaTimeMillis / 1000f
                totalFuelConsumed += litersPerSecond * deltaTimeSeconds * fuelCalibrationFactor
            }
        }
        
        lastTimestamp = now

        // 3. Update Maximums and Accumulators
        trip.maxSpeedKmh = maxOf(trip.maxSpeedKmh, currentSpeed)
        
        val currentRpm = data["010C"] ?: 0f
        trip.maxRpm = maxOf(trip.maxRpm, currentRpm)
        
        val currentTemp = data["0105"] ?: 0f
        trip.maxTempC = maxOf(trip.maxTempC, currentTemp)

        speedSum += currentSpeed
        rpmSum += currentRpm
        speedCount++
        rpmCount++

        speedHistory.add(currentSpeed)
        rpmHistory.add(currentRpm)
        data["0111"]?.let { throttleHistory.add(it) }

        // 4. Update Current Trip Object (Live)
        _currentTrip = trip.copy(
            distanceKm = totalDistance,
            durationSeconds = (now - trip.startedAt) / 1000,
            avgSpeedKmh = speedSum / speedCount,
            avgRpm = rpmSum / rpmCount,
            ecoScore = calculateEcoScore()
        )
    }

    private fun calculateEcoScore(): Int {
        if (speedHistory.size < 2) return 100
        
        var penalty = 0
        
        // Penalty: Harsh Acceleration (> 8 km/h change in approx 1s)
        val harshAccels = speedHistory.windowed(2).count { (prev, curr) -> (curr - prev) > 8f }
        penalty += harshAccels * 6
        
        // Penalty: High RPM (> 3500)
        val highRpmPoints = rpmHistory.count { it > 3500f }
        penalty += (highRpmPoints.toFloat() / rpmHistory.size * 60).toInt()
        
        // Penalty: Hard Braking (< -12 km/h change)
        val hardBraking = speedHistory.windowed(2).count { (prev, curr) -> (curr - prev) < -12f }
        penalty += hardBraking * 10
        
        // Penalty: High Throttle Position (> 70%)
        val highThrottle = throttleHistory.count { it > 70f }
        penalty += (highThrottle.toFloat() / throttleHistory.size * 30).toInt()

        return (100 - penalty).coerceIn(0, 100)
    }

    private fun resetAccumulators() {
        lastTimestamp = 0
        speedSum = 0f
        speedCount = 0
        rpmSum = 0f
        rpmCount = 0
        totalDistance = 0f
        totalFuelConsumed = 0f
        speedHistory.clear()
        rpmHistory.clear()
        throttleHistory.clear()
    }

    suspend fun endTrip(): TripEntity? {
        val tripEntity = _currentTrip?.copy(
            endedAt = System.currentTimeMillis()
        ) ?: return null
        
        // Convert to Domain Trip for Sync
        val domainTrip = com.elysium369.meet.data.supabase.Trip(
            id = tripEntity.id,
            user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
            vehicle_id = tripEntity.vehicleId,
            session_id = tripEntity.sessionId,
            started_at = tripEntity.startedAt,
            ended_at = tripEntity.endedAt,
            distance_km = tripEntity.distanceKm,
            duration_seconds = tripEntity.durationSeconds,
            avg_speed_kmh = tripEntity.avgSpeedKmh,
            max_speed_kmh = tripEntity.maxSpeedKmh,
            max_rpm = tripEntity.maxRpm,
            avg_rpm = tripEntity.avgRpm,
            max_temp_c = tripEntity.maxTempC,
            fuel_efficiency = tripEntity.fuelEfficiency,
            eco_score = tripEntity.ecoScore,
            gps_track_json = tripEntity.gpsTrackJson
        )

        // Save via Repository (Local + Remote attempt)
        tripRepository.saveTrip(domainTrip)
        
        monitoringJob?.cancel()
        _currentTrip = null
        resetAccumulators()
        
        return tripEntity
    }

    fun setFuelCalibration(factor: Float) {
        fuelCalibrationFactor = factor.coerceIn(0.5f, 2.0f)
    }
}
