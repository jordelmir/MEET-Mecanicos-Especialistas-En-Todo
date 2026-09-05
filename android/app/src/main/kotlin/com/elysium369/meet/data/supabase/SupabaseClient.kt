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
import com.elysium369.meet.core.remote.RemoteResult
import com.elysium369.meet.identity.ActivePrincipalKernel
import com.elysium369.meet.identity.OfflineOwnership
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

/** Wire model for the production `cloud_vehicles` table. */
@Serializable
private data class CloudVehicle(
    val id: String,
    val user_id: String,
    val vin: String? = null,
    val make: String,
    val model: String,
    val year: Int? = null,
    val engine: String? = null,
    val plate: String? = null,
    val odometer: Int = 0,
    val nickname: String? = null,
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

@Serializable
data class DtcEvent(
    val id: String,
    val session_id: String,
    val vehicle_id: String,
    val code: String,
    val description: String,
    val severity: String,
    val status: String,
    val first_seen_at: Long,
    val last_seen_at: Long,
    val resolved_at: Long?,
    val occurrence_count: Int,
    val freeze_frame: String? = null,
    val created_at: String? = null
)

@Serializable
data class RemoteDtcDefinition(
    val code: String,
    val manufacturer: String = "GENERIC",
    val description_es: String,
    val description_en: String,
    val system: String,
    val severity: String,
    val possible_causes: String,
    val urgency: String
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
    fun getVehiclesForUser(userId: String): Flow<List<Vehicle>> {
        return vehicleDao.getAllVehiclesForUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun syncVehiclesFromCloud(userId: String): RemoteResult<Int> {
        return try {
            val cloudVehicles = SupabaseManager.client.postgrest["cloud_vehicles"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }.decodeList<CloudVehicle>()
            
            cloudVehicles.forEach { vehicle ->
                vehicleDao.insertVehicle(vehicle.toLocalVehicle().toEntity())
            }
            RemoteResult.Success(cloudVehicles.size)
        } catch (e: Exception) {
            android.util.Log.e("VehicleRepository", "Failed to sync vehicles from cloud", e)
            when {
                e.message?.contains("auth", ignoreCase = true) == true ||
                e.message?.contains("jwt", ignoreCase = true) == true ->
                    RemoteResult.Unauthorized
                e.message?.contains("timeout", ignoreCase = true) == true ||
                e.message?.contains("connect", ignoreCase = true) == true ->
                    RemoteResult.TransportFailure(code = "SYNC_FAILED", message = e.message)
                else -> RemoteResult.ServerFailure(code = "SYNC_FAILED", message = e.message)
            }
        }
    }

    suspend fun getVehicleById(userId: String, id: String): Vehicle? {
        return vehicleDao.getVehicleByIdForUser(userId, id)?.toDomain()
    }

    suspend fun getVehicleByVin(userId: String, vin: String): Vehicle? {
        val cleanVin = vin.trim().uppercase()
        if (cleanVin.isBlank() || cleanVin == "N/A" || cleanVin == "NOT_READ") return null
        return vehicleDao.getVehicleByVinForUser(userId, cleanVin)?.toDomain()
    }
    
    suspend fun insertVehicle(vehicle: Vehicle): RemoteResult<Unit> {
        // Save locally
        vehicleDao.insertVehicle(vehicle.toEntity())
        
        // Sync to cloud
        return try {
            val authenticatedUserId = SupabaseManager.client.auth.currentUserOrNull()?.id
            if (authenticatedUserId != vehicle.user_id) {
                return RemoteResult.Forbidden(code = "USER_MISMATCH", message = "Authenticated user does not match vehicle owner")
            }
            SupabaseManager.client.postgrest["cloud_vehicles"].upsert(vehicle.toCloudVehicle())
            RemoteResult.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("VehicleRepository", "Failed to push vehicle to cloud", e)
            when {
                e.message?.contains("auth", ignoreCase = true) == true ->
                    RemoteResult.Unauthorized
                else -> RemoteResult.TransportFailure(code = "PUSH_FAILED", message = e.message)
            }
        }
    }

    suspend fun deleteVehicle(vehicle: Vehicle): RemoteResult<Unit> {
        // Delete locally
        vehicleDao.deleteVehicle(vehicle.toEntity())
        
        // Delete from cloud
        return try {
            SupabaseManager.client.postgrest["cloud_vehicles"].delete {
                filter {
                    eq("id", vehicle.id)
                    eq("user_id", vehicle.user_id)
                }
            }
            RemoteResult.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("VehicleRepository", "Failed to delete vehicle from cloud", e)
            when {
                e.message?.contains("auth", ignoreCase = true) == true ->
                    RemoteResult.Unauthorized
                else -> RemoteResult.TransportFailure(code = "DELETE_FAILED", message = e.message)
            }
        }
    }
}

private fun CloudVehicle.toLocalVehicle() = Vehicle(
    id = id,
    user_id = user_id,
    year = year ?: 0,
    make = make,
    model = model,
    engine = engine ?: "Dato no capturado",
    vin = vin ?: "NOT_READ",
    plate = plate ?: "NOT_SET",
)

private fun Vehicle.toCloudVehicle() = CloudVehicle(
    id = id,
    user_id = user_id,
    vin = vin.takeUnless { it == "NOT_READ" },
    make = make,
    model = model,
    year = year.takeIf { it > 0 },
    engine = engine.takeUnless { it == "Dato no capturado" },
    plate = plate.takeUnless { it == "NOT_SET" },
)

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
    private val activePrincipalKernel: ActivePrincipalKernel,
    @ApplicationContext private val context: Context
) {
    suspend fun saveSession(session: DiagnosticSession, canonicalVehicleId: String) {
        require(canonicalVehicleId.isNotBlank()) { "Canonical vehicle id is required" }
        val principal = activePrincipalKernel.current()
        // 1. Save locally first (Always succeeds)
        val entity = DiagnosticSessionEntity(
            id = session.id ?: java.util.UUID.randomUUID().toString(),
            vehicleId = canonicalVehicleId,
            observedVin = session.vehicle_vin ?: "LEGACY_NOT_CAPTURED",
            adapterFingerprint = session.adapter_type,
            protocolUsed = "Auto",
            startedAt = System.currentTimeMillis(),
            endedAt = System.currentTimeMillis(),
            dtcSnapshot = session.dtcs_found,
            liveDataSummary = session.live_data_snapshot,
            synced = false,
            ownerPrincipalId = principal.id,
            tenantId = OfflineOwnership.PERSONAL_TENANT,
            originDeviceId = activePrincipalKernel.localDeviceId,
            createdOffline = true,
        )
        sessionDao.insertSession(entity)

        // 2. Try immediate sync if network is available
        if (!principal.canSyncToCloud) return
        try {
            SupabaseManager.client.postgrest["scan_sessions"].insert(
                session.copy(user_id = principal.id),
            )
            sessionDao.markAsSynced(listOf(entity.id), principal.id)
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
    private val activePrincipalKernel: ActivePrincipalKernel,
    @ApplicationContext private val context: Context
) {
    suspend fun saveTrip(trip: Trip, ownerPrincipalId: String) {
        val principal = activePrincipalKernel.current()
        require(ownerPrincipalId == trip.user_id) { "Trip actor and immutable owner must match" }
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
            synced = false,
            ownerPrincipalId = ownerPrincipalId,
            tenantId = OfflineOwnership.PERSONAL_TENANT,
            originDeviceId = activePrincipalKernel.localDeviceId,
            createdOffline = true,
        )
        tripDao.insertTrip(entity)

        // 2. Try immediate sync
        if (!com.elysium369.meet.identity.OfflineOwnership.canSync(ownerPrincipalId, principal)) return
        try {
            SupabaseManager.client.postgrest["trips"].insert(trip)
            tripDao.markAsSynced(listOf(entity.id), ownerPrincipalId)
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

fun com.elysium369.meet.data.local.entities.DtcEventEntity.toDomain() = DtcEvent(
    id = id,
    session_id = sessionId,
    vehicle_id = vehicleId,
    code = code,
    description = description,
    severity = severity,
    status = status,
    first_seen_at = firstSeenAt,
    last_seen_at = lastSeenAt,
    resolved_at = resolvedAt,
    occurrence_count = occurrenceCount,
    freeze_frame = freezeFrameJson
)
