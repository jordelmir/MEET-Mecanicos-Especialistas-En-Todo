package com.elysium369.meet.ride.schedule

import android.util.Log
import com.elysium369.meet.data.local.dao.ScheduledRideDao
import com.elysium369.meet.data.local.entities.FavoriteRouteEntity
import com.elysium369.meet.data.local.entities.ScheduledRideEntity
import com.elysium369.meet.ride.domain.RideFareMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ══════════════════════════════════════════════════════════════════════
 *  R I D E   S C H E D U L E   E N G I N E
 *  ──────────────────────────────────────────
 *  "Necesito un viaje mañana a las 6am al aeropuerto."
 *
 *  Backed by Room for persistence across process death.
 *  Server-side Supabase infra already exists for sync.
 * ══════════════════════════════════════════════════════════════════════
 */

// ─── Schedule Status ───

enum class ScheduleStatus {
    PENDING,
    REMINDER_SENT,
    DISPATCHING,
    DRIVER_MATCHED,
    ACTIVE,
    COMPLETED,
    CANCELLED,
    EXPIRED,
}

// ─── Recurrence ───

enum class RecurrencePattern {
    NONE,
    DAILY,
    WEEKDAYS,
    WEEKLY,
    CUSTOM,
}

@Serializable
data class RecurrenceConfig(
    val pattern: RecurrencePattern = RecurrencePattern.NONE,
    val customDays: Set<Int> = emptySet(),
    val endAfterTrips: Int? = null,
    val endAtEpochMs: Long? = null,
)

// ─── Stop ───

@Serializable
data class RideStop(
    val stopId: String,
    val displayName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val waitMinutes: Int = 0,
    val order: Int,
    val isPickup: Boolean = false,
    val isDropoff: Boolean = false,
)

// ─── Favorite Route ───

@Serializable
data class FavoriteRoute(
    val routeId: String,
    val label: String,
    val icon: String = "🏠",
    val stops: List<RideStop>,
    val fareMode: RideFareMode = RideFareMode.METERED_TIME_DISTANCE,
    val usageCount: Int = 0,
    val lastUsedEpochMs: Long? = null,
)

// ─── Scheduled Ride ───

@Serializable
data class ScheduledRide(
    val scheduleId: String,
    val userId: String,
    val stops: List<RideStop>,
    val scheduledAtEpochMs: Long,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val fareMode: RideFareMode = RideFareMode.METERED_TIME_DISTANCE,
    val estimatedFare: Long = 0,
    val currency: String,
    val status: ScheduleStatus = ScheduleStatus.PENDING,
    val recurrence: RecurrenceConfig = RecurrenceConfig(),
    val notes: String = "",
    val matchedDriverId: String? = null,
    val rideId: String? = null,
    val dispatchAtEpochMs: Long = scheduledAtEpochMs - 15 * 60 * 1000,
) {
    val isPending: Boolean get() = status == ScheduleStatus.PENDING
    val isRecurring: Boolean get() = recurrence.pattern != RecurrencePattern.NONE
    val stopCount: Int get() = stops.size
    val isMultiStop: Boolean get() = stops.size > 2

    val pickup: RideStop? get() = stops.firstOrNull { it.isPickup }
    val dropoff: RideStop? get() = stops.lastOrNull { it.isDropoff }

    val formattedFare: String
        get() = com.elysium369.meet.ride.domain.RideTrackingTruthPolicy.formatFare(estimatedFare, currency)
}

// ─── Reminder ───

data class ScheduleReminder(
    val scheduleId: String,
    val type: ReminderType,
    val message: String,
    val triggerAtEpochMs: Long,
)

enum class ReminderType {
    ONE_HOUR,
    THIRTY_MIN,
    FIFTEEN_MIN,
    DRIVER_MATCHED,
}

// ─── Engine ───

@Singleton
class RideScheduleEngine @Inject constructor(
    private val scheduleDao: ScheduledRideDao,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ─── Schedule a Ride ───

    suspend fun scheduleRide(
        userId: String,
        stops: List<RideStop>,
        scheduledAtEpochMs: Long,
        fareMode: RideFareMode = RideFareMode.METERED_TIME_DISTANCE,
        estimatedFare: Long = 0,
        currency: String = "CRC",
        recurrence: RecurrenceConfig = RecurrenceConfig(),
        notes: String = "",
    ): ScheduledRide? {
        if (stops.size < 2) return null
        if (scheduledAtEpochMs <= System.currentTimeMillis()) return null

        val now = System.currentTimeMillis()
        val ride = ScheduledRide(
            scheduleId = "sched-$now",
            userId = userId,
            stops = stops,
            scheduledAtEpochMs = scheduledAtEpochMs,
            createdAtEpochMs = now,
            fareMode = fareMode,
            estimatedFare = estimatedFare,
            currency = currency,
            recurrence = recurrence,
            notes = notes,
        )
        scheduleDao.upsert(ride.toEntity())
        Log.i("RideScheduleEngine", "Scheduled ride: ${ride.scheduleId} at $scheduledAtEpochMs")
        return ride
    }

    // ─── Multi-Stop ───

    suspend fun addStop(scheduleId: String, stop: RideStop): Boolean {
        val entity = scheduleDao.getById(scheduleId) ?: return false
        val ride = entity.toDomain()
        if (!ride.isPending) return false
        val updated = ride.copy(stops = (ride.stops + stop).sortedBy { it.order })
        scheduleDao.upsert(updated.toEntity())
        return true
    }

    suspend fun removeStop(scheduleId: String, stopId: String): Boolean {
        val entity = scheduleDao.getById(scheduleId) ?: return false
        val ride = entity.toDomain()
        if (!ride.isPending) return false
        val updated = ride.stops.filter { it.stopId != stopId }
        if (updated.size < 2) return false
        scheduleDao.upsert(ride.copy(stops = updated).toEntity())
        return true
    }

    // ─── Reminders ───

    suspend fun generateReminders(scheduleId: String): List<ScheduleReminder> {
        val entity = scheduleDao.getById(scheduleId) ?: return emptyList()
        val ride = entity.toDomain()
        val pickup = ride.pickup?.displayName ?: "origen"

        return listOf(
            ScheduleReminder(
                scheduleId = scheduleId,
                type = ReminderType.ONE_HOUR,
                message = "Tu viaje a $pickup es en 1 hora",
                triggerAtEpochMs = ride.scheduledAtEpochMs - 60 * 60 * 1000,
            ),
            ScheduleReminder(
                scheduleId = scheduleId,
                type = ReminderType.THIRTY_MIN,
                message = "Tu viaje a $pickup es en 30 minutos",
                triggerAtEpochMs = ride.scheduledAtEpochMs - 30 * 60 * 1000,
            ),
            ScheduleReminder(
                scheduleId = scheduleId,
                type = ReminderType.FIFTEEN_MIN,
                message = "Buscando conductor para tu viaje a $pickup...",
                triggerAtEpochMs = ride.scheduledAtEpochMs - 15 * 60 * 1000,
            ),
        )
    }

    // ─── Dispatch ───

    suspend fun dispatch(scheduleId: String): ScheduledRide? {
        val entity = scheduleDao.getById(scheduleId) ?: return null
        val ride = entity.toDomain()
        if (!ride.isPending) return null
        val updated = ride.copy(status = ScheduleStatus.DISPATCHING)
        scheduleDao.upsert(updated.toEntity())
        return updated
    }

    suspend fun assignDriver(scheduleId: String, driverId: String, rideId: String): ScheduledRide? {
        val entity = scheduleDao.getById(scheduleId) ?: return null
        val ride = entity.toDomain()
        if (ride.status != ScheduleStatus.DISPATCHING) return null
        val updated = ride.copy(
            status = ScheduleStatus.DRIVER_MATCHED,
            matchedDriverId = driverId,
            rideId = rideId,
        )
        scheduleDao.upsert(updated.toEntity())
        return updated
    }

    // ─── Cancel ───

    suspend fun cancel(scheduleId: String): Boolean {
        val entity = scheduleDao.getById(scheduleId) ?: return false
        val ride = entity.toDomain()
        if (ride.status in listOf(ScheduleStatus.COMPLETED, ScheduleStatus.CANCELLED)) return false
        scheduleDao.upsert(ride.copy(status = ScheduleStatus.CANCELLED).toEntity())
        return true
    }

    // ─── Favorites ───

    suspend fun saveFavoriteRoute(label: String, icon: String, stops: List<RideStop>): FavoriteRoute {
        val route = FavoriteRoute(
            routeId = "fav-${System.currentTimeMillis()}",
            label = label,
            icon = icon,
            stops = stops,
        )
        scheduleDao.upsertFavorite(route.toEntity())
        return route
    }

    suspend fun bookFromFavorite(routeId: String, userId: String, scheduledAt: Long): ScheduledRide? {
        val entity = scheduleDao.getFavoriteById(routeId) ?: return null
        val fav = entity.toDomain()
        scheduleDao.incrementFavoriteUsage(routeId, System.currentTimeMillis())
        return scheduleRide(userId, fav.stops, scheduledAt, fav.fareMode)
    }

    suspend fun getFavorites(): List<FavoriteRoute> = scheduleDao.getFavorites().map { it.toDomain() }

    // ─── Queries ───

    suspend fun getUpcoming(userId: String): List<ScheduledRide> =
        scheduleDao.getUpcoming(userId).map { it.toDomain() }

    suspend fun getSchedule(id: String): ScheduledRide? =
        scheduleDao.getById(id)?.toDomain()

    // ─── Cleanup ───

    suspend fun cleanup(maxAgeMs: Long = 30 * 24 * 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        scheduleDao.purgeTerminal(cutoff)
    }

    // ── Entity ↔ Domain mapping ──

    private fun ScheduledRide.toEntity() = ScheduledRideEntity(
        scheduleId = scheduleId,
        userId = userId,
        stopsJson = json.encodeToString(stops),
        scheduledAtEpochMs = scheduledAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        fareMode = fareMode.name,
        estimatedFare = estimatedFare,
        currency = currency,
        status = status.name,
        recurrencePattern = recurrence.pattern.name,
        recurrenceConfigJson = json.encodeToString(recurrence),
        notes = notes,
        matchedDriverId = matchedDriverId,
        rideId = rideId,
        dispatchAtEpochMs = dispatchAtEpochMs,
    )

    private fun ScheduledRideEntity.toDomain() = ScheduledRide(
        scheduleId = scheduleId,
        userId = userId,
        stops = try { json.decodeFromString(stopsJson) } catch (_: Exception) { emptyList() },
        scheduledAtEpochMs = scheduledAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        fareMode = try { RideFareMode.valueOf(fareMode) } catch (_: Exception) { RideFareMode.METERED_TIME_DISTANCE },
        estimatedFare = estimatedFare,
        currency = currency,
        status = try { ScheduleStatus.valueOf(status) } catch (_: Exception) { ScheduleStatus.PENDING },
        recurrence = try {
            recurrenceConfigJson?.let { json.decodeFromString<RecurrenceConfig>(it) } ?: RecurrenceConfig()
        } catch (_: Exception) { RecurrenceConfig() },
        notes = notes,
        matchedDriverId = matchedDriverId,
        rideId = rideId,
        dispatchAtEpochMs = dispatchAtEpochMs ?: (scheduledAtEpochMs - 15 * 60 * 1000),
    )

    private fun FavoriteRoute.toEntity() = FavoriteRouteEntity(
        routeId = routeId,
        label = label,
        icon = icon,
        stopsJson = json.encodeToString(stops),
        fareMode = fareMode.name,
        usageCount = usageCount,
        lastUsedEpochMs = lastUsedEpochMs ?: 0L,
    )

    private fun FavoriteRouteEntity.toDomain() = FavoriteRoute(
        routeId = routeId,
        label = label,
        icon = icon,
        stops = try { json.decodeFromString(stopsJson) } catch (_: Exception) { emptyList() },
        fareMode = try { RideFareMode.valueOf(fareMode) } catch (_: Exception) { RideFareMode.METERED_TIME_DISTANCE },
        usageCount = usageCount,
        lastUsedEpochMs = if (lastUsedEpochMs > 0) lastUsedEpochMs else null,
    )
}
