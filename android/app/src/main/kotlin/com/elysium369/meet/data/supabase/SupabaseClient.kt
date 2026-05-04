package com.elysium369.meet.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import com.elysium369.meet.data.local.dao.VehicleDao
import com.elysium369.meet.data.local.dao.DiagnosticSessionDao
import com.elysium369.meet.data.local.entities.VehicleEntity
import com.elysium369.meet.data.local.entities.DiagnosticSessionEntity
import com.elysium369.meet.core.sync.SyncWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

@Serializable
data class Vehicle(
    val id: String,
    val user_id: String,
    val year: Int,
    val make: String,
    val model: String,
    val engine: String,
    val displacement_cc: Int = 0,
    val engine_tech: String = "",
    val transmission_type: String = "",
    val transmission_subtype: String = "",
    val fuel_type: String = "",
    val vin: String,
    val plate: String
)

@Serializable
data class DiagnosticSession(
    val id: String? = null,
    val user_id: String,
    val vehicle_vin: String? = null,
    val vehicle_make: String? = null,
    val vehicle_model: String? = null,
    val vehicle_year: Int? = null,
    val vehicle_plate: String? = null,
    val adapter_type: String = "clone",
    val scan_type: String = "quick",
    val dtcs_found: String = "[]", // Store as JSON string
    val severity: String = "low",
    val live_data_snapshot: String = "{}",
    val freeze_frame: String = "{}",
    val notes: String? = null,
    val created_at: String? = null
)

@Serializable
data class Trip(
    val id: String,
    val user_id: String,
    val vehicle_id: String,
    val session_id: String,
    val started_at: Long,
    val ended_at: Long?,
    val distance_km: Float,
    val duration_seconds: Long,
    val avg_speed_kmh: Float,
    val max_speed_kmh: Float,
    val max_rpm: Float,
    val avg_rpm: Float,
    val max_temp_c: Float,
    val fuel_efficiency: Float?,
    val eco_score: Int,
    val gps_track_json: String?,
    val created_at: String? = null
)

object SupabaseManager {
    val client get() = com.elysium369.meet.data.remote.SupabaseModule.client

    suspend fun isUserPremium(): Boolean {
        val user = client.auth.currentUserOrNull() ?: return false
        return try {
            val response = client.postgrest["subscriptions"]
                .select {
                    filter {
                        eq("user_id", user.id)
                        eq("status", "active")
                    }
                }.decodeSingleOrNull<SubscriptionInfo>()
            response?.plan == "elite" || response?.plan == "pro"
        } catch (e: Exception) {
            false
        }
    }

    @Serializable
    private data class SubscriptionInfo(val plan: String, val status: String)
}

@Singleton
class VehicleRepository @Inject constructor(
    private val vehicleDao: VehicleDao
) {
    fun getVehiclesForUser(): Flow<List<Vehicle>> {
        return vehicleDao.getAllVehicles().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun syncVehiclesFromCloud(userId: String) {
        try {
            val cloudVehicles = SupabaseManager.client.postgrest["vehicles"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }.decodeList<Vehicle>()
            
            cloudVehicles.forEach { vehicle ->
                vehicleDao.insertVehicle(vehicle.toEntity())
            }
        } catch (e: Exception) {
            android.util.Log.e("VehicleRepository", "Failed to sync vehicles from cloud", e)
        }
    }

    suspend fun getVehicleById(id: String): Vehicle? {
        return vehicleDao.getVehicleById(id)?.toDomain()
    }
    
    suspend fun insertVehicle(vehicle: Vehicle) {
        // Save locally
        vehicleDao.insertVehicle(vehicle.toEntity())
        
        // Sync to cloud
        try {
            SupabaseManager.client.postgrest["vehicles"].upsert(vehicle)
        } catch (e: Exception) {
            android.util.Log.e("VehicleRepository", "Failed to push vehicle to cloud", e)
        }
    }

    suspend fun deleteVehicle(vehicle: Vehicle) {
        // Delete locally
        vehicleDao.deleteVehicle(vehicle.toEntity())
        
        // Delete from cloud
        try {
            SupabaseManager.client.postgrest["vehicles"].delete {
                filter {
                    eq("id", vehicle.id)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VehicleRepository", "Failed to delete vehicle from cloud", e)
        }
    }
}

fun VehicleEntity.toDomain() = Vehicle(
    id = id,
    user_id = userId,
    year = year,
    make = make,
    model = model,
    engine = engine,
    displacement_cc = displacementCc,
    engine_tech = engineTech,
    transmission_type = transmissionType,
    transmission_subtype = transmissionSubtype,
    fuel_type = fuelType,
    vin = vin,
    plate = plate
)

fun Vehicle.toEntity() = VehicleEntity(
    id = id,
    userId = user_id,
    year = year,
    make = make,
    model = model,
    engine = engine,
    displacementCc = displacement_cc,
    engineTech = engine_tech,
    transmissionType = transmission_type,
    transmissionSubtype = transmission_subtype,
    fuelType = fuel_type,
    vin = vin,
    plate = plate,
    photoPath = null,
    odometerKm = 0L,
    createdAt = System.currentTimeMillis(),
    syncedAt = null
)

@Singleton
class SessionLogRepository @Inject constructor(
    private val sessionDao: DiagnosticSessionDao,
    private val dtcDao: com.elysium369.meet.data.local.dao.DtcDao,
    @ApplicationContext private val context: Context
) {
    suspend fun saveSession(session: DiagnosticSession) {
        // 1. Save locally first (Always succeeds)
        val entity = DiagnosticSessionEntity(
            id = session.id ?: java.util.UUID.randomUUID().toString(),
            vehicleId = session.vehicle_vin ?: "unknown",
            adapterFingerprint = session.adapter_type,
            protocolUsed = "Auto",
            startedAt = System.currentTimeMillis(),
            endedAt = System.currentTimeMillis(),
            dtcSnapshot = session.dtcs_found,
            liveDataSummary = session.live_data_snapshot,
            synced = false
        )
        sessionDao.insertSession(entity)

        // 2. Try immediate sync if network is available
        try {
            SupabaseManager.client.postgrest["scan_sessions"].insert(session)
            sessionDao.markAsSynced(listOf(entity.id))
        } catch (e: Exception) {
            // 3. Fallback: Schedule background sync if immediate fails
            scheduleBackgroundSync()
        }
    }

    private fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(syncRequest)
    }

    fun getSessions(userId: String): Flow<List<DiagnosticSession>> = flow {
        // Combined flow from local + remote would be ideal, but for now just fetching remote
        try {
            val response = SupabaseManager.client.postgrest["scan_sessions"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order("created_at", Order.DESCENDING)
                }.decodeList<DiagnosticSession>()
            emit(response)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }
}

@Singleton
class TripRepository @Inject constructor(
    private val tripDao: com.elysium369.meet.data.local.dao.TripDao,
    @ApplicationContext private val context: Context
) {
    suspend fun saveTrip(trip: Trip) {
        // 1. Save locally
        val entity = com.elysium369.meet.data.local.entities.TripEntity(
            id = trip.id,
            vehicleId = trip.vehicle_id,
            sessionId = trip.session_id,
            startedAt = trip.started_at,
            endedAt = trip.ended_at,
            distanceKm = trip.distance_km,
            durationSeconds = trip.duration_seconds,
            avgSpeedKmh = trip.avg_speed_kmh,
            maxSpeedKmh = trip.max_speed_kmh,
            maxRpm = trip.max_rpm,
            avgRpm = trip.avg_rpm,
            maxTempC = trip.max_temp_c,
            fuelEfficiency = trip.fuel_efficiency,
            ecoScore = trip.eco_score,
            gpsTrackJson = trip.gps_track_json,
            synced = false
        )
        tripDao.insertTrip(entity)

        // 2. Try immediate sync
        try {
            SupabaseManager.client.postgrest["trips"].insert(trip)
            tripDao.markAsSynced(listOf(entity.id))
        } catch (e: Exception) {
            // 3. Fallback to background sync
            scheduleBackgroundSync()
        }
    }

    private fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(syncRequest)
    }
}

@Singleton
class SubscriptionRepository @Inject constructor() {
    suspend fun isPremium(): Boolean {
        return SupabaseManager.isUserPremium()
    }
}
