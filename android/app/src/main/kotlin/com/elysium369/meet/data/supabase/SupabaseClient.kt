package com.elysium369.meet.data.supabase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import com.elysium369.meet.data.local.dao.VehicleDao
import com.elysium369.meet.data.local.entities.VehicleEntity
import kotlinx.coroutines.flow.map

@Serializable
data class Vehicle(
    val id: String,
    val user_id: String,
    val year: Int,
    val make: String,
    val model: String,
    val engine: String,
    val vin: String,
    val plate: String
)

@Serializable
data class DiagnosticSession(
    val id: String,
    val user_id: String,
    val vehicle_id: String,
    val adapter_fingerprint: String,
    val protocol_used: String,
    val started_at: String,
    val ended_at: String?,
    val dtc_snapshot: String,
    val live_data_summary: String
)

object SupabaseManager {

    // Usar el cliente real de SupabaseModule (que tiene las credenciales correctas)
    // para evitar duplicación y credenciales placeholder rotas
    val client get() = com.elysium369.meet.data.remote.SupabaseModule.client

    suspend fun isUserPremium(): Boolean {
        // TODO: Wire to RevenueCat/Stripe subscription check
        return false
    }
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

    suspend fun getVehicleById(id: String): Vehicle? {
        return vehicleDao.getVehicleById(id)?.toDomain()
    }
    
    suspend fun insertVehicle(vehicle: Vehicle) {
        vehicleDao.insertVehicle(vehicle.toEntity())
    }
}

fun VehicleEntity.toDomain() = Vehicle(
    id = id,
    user_id = userId,
    year = year,
    make = make,
    model = model,
    engine = engine,
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
    vin = vin,
    plate = plate,
    photoPath = null,
    odometerKm = 0L,
    createdAt = System.currentTimeMillis(),
    syncedAt = null
)

@Singleton
class SessionLogRepository @Inject constructor() {
    suspend fun saveSession(session: DiagnosticSession) {
        // SupabaseManager.client.postgrest["diagnostic_sessions"].insert(session)
    }

    fun getSessions(vehicleId: String): Flow<List<DiagnosticSession>> = flow {
        emit(emptyList())
    }
}

@Singleton
class SubscriptionRepository @Inject constructor() {
    suspend fun isPremium(): Boolean {
        return SupabaseManager.isUserPremium()
    }
}
