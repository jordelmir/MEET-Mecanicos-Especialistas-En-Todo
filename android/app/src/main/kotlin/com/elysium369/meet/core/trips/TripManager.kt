package com.elysium369.meet.core.trips

import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.data.local.dao.TripDao
import com.elysium369.meet.data.local.entities.TripEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import io.github.jan.supabase.gotrue.auth

/**
 * TripManager — Professional trip tracking and telemetry analysis engine.
 * Calculates distance, fuel consumption, and driving behavior (EcoScore) in real-time.
 *
 * v2.1 — Hardened:
 *   - Doze-safe delta clamping prevents phantom distance after device sleep
 *   - Multiplicative fuel trim correction (SAE standard)
 *   - ArrayDeque for O(1) history rotation instead of O(n) ArrayList.removeAt(0)
 *   - Single-pass EcoScore calculation eliminates ~4000 temporary allocations/tick
 *   - fuelEfficiency (km/L) is now computed on trip end
 *   - endTrip() uses try/finally to guarantee cleanup on sync failure
 */
@Singleton
class TripManager @Inject constructor(
    private val obdSession: ObdSession,
    private val tripRepository: com.elysium369.meet.data.supabase.TripRepository,
    private val phoneSpeedTracker: com.elysium369.meet.core.obd.PhoneSpeedTracker,
    private val scope: CoroutineScope
) {
    private var _currentTrip: TripEntity? = null
    private val _currentTripState = MutableStateFlow<TripEntity?>(null)
    val currentTripState: StateFlow<TripEntity?> = _currentTripState.asStateFlow()
    val currentTrip: TripEntity? get() = _currentTrip
    
    private var monitoringJob: Job? = null
    
    // Telemetry Accumulators
    private var lastDistanceTimestamp: Long = 0
    private var lastFuelTimestamp: Long = 0
    private var speedSum = 0f
    private var speedCount = 0
    private var rpmSum = 0f
    private var rpmCount = 0
    private var totalDistance = 0f // Km
    private var totalFuelConsumed = 0f // Liters (Estimated)
    private var maxSpeed = 0f
    private var maxRpm = 0f
    private var maxTemp = 0f
    
    private var fuelCalibrationFactor = 1.0f // Multiplier for fine-tuning

    // Maximum delta time to prevent phantom distance after Doze/sleep wake-up
    private companion object {
        const val MAX_DELTA_MILLIS = 3000L // 3s — any gap larger is clamped
        const val HISTORY_MAX_SIZE = 1000
        const val STOICH_GASOLINE = 14.7f
        const val GASOLINE_DENSITY_GPL = 740f // grams per liter
    }

    // ArrayDeque for O(1) removeFirst() instead of ArrayList's O(n) removeAt(0)
    private val speedHistory = ArrayDeque<Float>(HISTORY_MAX_SIZE + 16)
    private val rpmHistory = ArrayDeque<Float>(HISTORY_MAX_SIZE + 16)
    private val throttleHistory = ArrayDeque<Float>(HISTORY_MAX_SIZE + 16)

    fun getSpeedHistory(): List<Float> = synchronized(speedHistory) { speedHistory.toList() }
    fun getRpmHistory(): List<Float> = synchronized(rpmHistory) { rpmHistory.toList() }
    fun getThrottleHistory(): List<Float> = synchronized(throttleHistory) { throttleHistory.toList() }

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
        _currentTripState.value = _currentTrip

        phoneSpeedTracker.start()

        monitoringJob = scope.launch(Dispatchers.IO) {
            // Loop de 1s para integración de distancia continua usando PhoneSpeedTracker (GPS + OBD)
            launch {
                lastDistanceTimestamp = System.currentTimeMillis()
                while (isActive) {
                    delay(1000L)
                    val now = System.currentTimeMillis()
                    val rawDelta = now - lastDistanceTimestamp
                    // Clamp delta to prevent phantom distance after Doze/sleep
                    val deltaTimeMillis = rawDelta.coerceAtMost(MAX_DELTA_MILLIS)
                    val deltaTimeHours = deltaTimeMillis / 3600000f
                    val currentSpeed = phoneSpeedTracker.fusedSpeed.value
                    
                    if (deltaTimeMillis > 0 && currentSpeed >= 0f) {
                        totalDistance += currentSpeed * deltaTimeHours
                    }
                    
                    _currentTrip?.let { trip ->
                        _currentTrip = trip.copy(
                            distanceKm = totalDistance,
                            durationSeconds = (now - trip.startedAt) / 1000,
                            avgSpeedKmh = if (speedCount > 0) speedSum / speedCount else currentSpeed
                        )
                        _currentTripState.value = _currentTrip
                    }
                    lastDistanceTimestamp = now
                }
            }

            // Colección asíncrona de datos de sensores OBD
            obdSession.liveData.collectLatest { data ->
                updateTelemetry(data)
            }
        }
    }

    private fun updateTelemetry(data: Map<String, Float>) {
        val trip = _currentTrip ?: return
        val now = System.currentTimeMillis()
        
        // Mantener PhoneSpeedTracker alimentado con la velocidad del OBD si está disponible
        val obdSpeed = data["010D"]
        if (obdSpeed != null) {
            phoneSpeedTracker.setObdSpeed(obdSpeed)
        }
        
        // 2. Estimación del consumo de combustible usando lastFuelTimestamp independiente
        val rawFuelDelta = if (lastFuelTimestamp > 0) (now - lastFuelTimestamp) else 0L
        // Clamp fuel delta too to prevent spurious fuel accounting after sleep
        val deltaTimeMillis = rawFuelDelta.coerceAtMost(MAX_DELTA_MILLIS)
        val deltaTimeHours = deltaTimeMillis / 3600000f
        
        val fuelRate = data["015E"] // L/h
        if (fuelRate != null && fuelRate > 0f && fuelRate < 100f) {
            // Sanity check: passenger cars rarely exceed 100 L/h
            totalFuelConsumed += fuelRate * deltaTimeHours
        } else {
            val maf = data["0110"] // g/s
            if (maf != null && maf > 0f) {
                val stft = data["0106"] ?: 0f
                val ltft = data["0107"] ?: 0f
                // Correct: multiplicative fuel trim (SAE standard)
                val totalTrimMultiplier = (1.0f + stft / 100f) * (1.0f + ltft / 100f)
                val baseFuelGps = maf / STOICH_GASOLINE
                val correctedFuelGps = baseFuelGps * totalTrimMultiplier
                val litersPerSecond = correctedFuelGps / GASOLINE_DENSITY_GPL
                val deltaTimeSeconds = deltaTimeMillis / 1000f
                totalFuelConsumed += litersPerSecond * deltaTimeSeconds * fuelCalibrationFactor
            }
        }
        lastFuelTimestamp = now

        // 3. Actualizar máximos y acumuladores
        val currentSpeed = obdSpeed ?: phoneSpeedTracker.fusedSpeed.value
        maxSpeed = maxOf(maxSpeed, currentSpeed)
        
        val currentRpm = data["010C"] ?: 0f
        if (currentRpm > 0f) {
            maxRpm = maxOf(maxRpm, currentRpm)
            rpmSum += currentRpm
            rpmCount++
            synchronized(rpmHistory) {
                rpmHistory.addLast(currentRpm)
                if (rpmHistory.size > HISTORY_MAX_SIZE) rpmHistory.removeFirst()
            }
        }
        
        val currentTemp = data["0105"] ?: 0f
        if (currentTemp > 0f) {
            maxTemp = maxOf(maxTemp, currentTemp)
        }

        speedSum += currentSpeed
        speedCount++
        synchronized(speedHistory) {
            speedHistory.addLast(currentSpeed)
            if (speedHistory.size > HISTORY_MAX_SIZE) speedHistory.removeFirst()
        }
        
        data["0111"]?.let { throttle ->
            synchronized(throttleHistory) {
                throttleHistory.addLast(throttle)
                if (throttleHistory.size > HISTORY_MAX_SIZE) throttleHistory.removeFirst()
            }
        }

        // 4. Actualizar estado en tiempo real (mantenido por el recolector OBD)
        _currentTrip = trip.copy(
            distanceKm = totalDistance,
            avgSpeedKmh = if (speedCount > 0) speedSum / speedCount else currentSpeed,
            avgRpm = if (rpmCount > 0) rpmSum / rpmCount else 0f,
            maxSpeedKmh = maxSpeed,
            maxRpm = maxRpm,
            maxTempC = maxTemp,
            ecoScore = calculateEcoScore()
        )
        _currentTripState.value = _currentTrip
    }

    private fun calculateEcoScore(): Int {
        // Thread-safe snapshots
        val speeds = synchronized(speedHistory) { speedHistory.toList() }
        val rpms = synchronized(rpmHistory) { rpmHistory.toList() }
        val throttles = synchronized(throttleHistory) { throttleHistory.toList() }

        if (speeds.size < 2) return 100
        
        var penalty = 0
        
        // Single-pass acceleration/braking analysis (eliminates double windowed(2) allocation)
        var harshAccels = 0
        var hardBraking = 0
        for (i in 1 until speeds.size) {
            val delta = speeds[i] - speeds[i - 1]
            if (delta > 8f) harshAccels++       // Harsh Acceleration (> 8 km/h change in ~1s)
            if (delta < -12f) hardBraking++     // Hard Braking (< -12 km/h change)
        }
        penalty += harshAccels * 6
        penalty += hardBraking * 10
        
        // Penalty: High RPM (> 3500)
        if (rpms.isNotEmpty()) {
            val highRpmPoints = rpms.count { it > 3500f }
            penalty += (highRpmPoints.toFloat() / rpms.size.coerceAtLeast(1) * 60).toInt()
        }
        
        // Penalty: High Throttle Position (> 70%)
        if (throttles.isNotEmpty()) {
            val highThrottle = throttles.count { it > 70f }
            penalty += (highThrottle.toFloat() / throttles.size.coerceAtLeast(1) * 30).toInt()
        }

        return (100 - penalty).coerceIn(0, 100)
    }

    private fun resetAccumulators() {
        lastDistanceTimestamp = 0
        lastFuelTimestamp = 0
        speedSum = 0f
        speedCount = 0
        rpmSum = 0f
        rpmCount = 0
        totalDistance = 0f
        totalFuelConsumed = 0f
        maxSpeed = 0f
        maxRpm = 0f
        maxTemp = 0f
        synchronized(speedHistory) { speedHistory.clear() }
        synchronized(rpmHistory) { rpmHistory.clear() }
        synchronized(throttleHistory) { throttleHistory.clear() }
    }

    suspend fun endTrip(): TripEntity? {
        val tripEntity = _currentTrip?.copy(
            endedAt = System.currentTimeMillis(),
            // Compute fuel efficiency (km/L) — was previously always null
            fuelEfficiency = if (totalFuelConsumed > 0.01f && totalDistance > 0.1f) {
                totalDistance / totalFuelConsumed
            } else null
        ) ?: return null
        
        try {
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
        } finally {
            // Guarantee cleanup even if sync fails — prevents orphaned monitoring
            monitoringJob?.cancel()
            phoneSpeedTracker.stop()
            _currentTrip = null
            _currentTripState.value = null
            resetAccumulators()
        }
        
        return tripEntity
    }

    fun setFuelCalibration(factor: Float) {
        fuelCalibrationFactor = factor.coerceIn(0.5f, 2.0f)
    }
}
