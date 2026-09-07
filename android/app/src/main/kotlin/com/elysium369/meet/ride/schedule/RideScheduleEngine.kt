package com.elysium369.meet.ride.schedule

import com.elysium369.meet.ride.domain.RideFareMode
import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  R I D E   S C H E D U L E   E N G I N E
 *  ──────────────────────────────────────────
 *  "Necesito un viaje mañana a las 6am al aeropuerto."
 *
 *  Schedule rides in advance:
 *  - Pick future date/time
 *  - Lock estimated fare
 *  - Auto-dispatch 15min before pickup
 *  - Reminders at 1h, 30min, 15min
 *  - Recurring schedules (daily commute)
 *  - Multi-stop support
 *  - Favorite routes for 1-tap rebooking
 * ══════════════════════════════════════════════════════════════════════
 */

// ─── Schedule Status ───

enum class ScheduleStatus {
    PENDING,        // Created, waiting for dispatch time
    REMINDER_SENT,  // 1h/30min reminder sent
    DISPATCHING,    // Auto-dispatch in progress (15min before)
    DRIVER_MATCHED, // Driver confirmed
    ACTIVE,         // Ride is live
    COMPLETED,      // Ride finished
    CANCELLED,      // User or system cancelled
    EXPIRED,        // No driver found in time
}

// ─── Recurrence ───

enum class RecurrencePattern {
    NONE,           // One-time
    DAILY,          // Every day
    WEEKDAYS,       // Mon-Fri
    WEEKLY,         // Same day every week
    CUSTOM,         // User-selected days
}

@Serializable
data class RecurrenceConfig(
    val pattern: RecurrencePattern = RecurrencePattern.NONE,
    val customDays: Set<Int> = emptySet(), // 1=Mon..7=Sun
    val endAfterTrips: Int? = null,        // Stop after N trips
    val endAtEpochMs: Long? = null,        // Stop at date
)

// ─── Stop (for multi-stop) ───

@Serializable
data class RideStop(
    val stopId: String,
    val displayName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val waitMinutes: Int = 0,   // How long to wait at this stop
    val order: Int,
    val isPickup: Boolean = false,
    val isDropoff: Boolean = false,
)

// ─── Favorite Route ───

@Serializable
data class FavoriteRoute(
    val routeId: String,
    val label: String,          // "Al trabajo", "Al aeropuerto"
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
    val scheduledAtEpochMs: Long,      // When the ride should happen
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val fareMode: RideFareMode = RideFareMode.METERED_TIME_DISTANCE,
    val estimatedFare: Long = 0,       // Locked fare estimate
    val currency: String = "CRC",
    val status: ScheduleStatus = ScheduleStatus.PENDING,
    val recurrence: RecurrenceConfig = RecurrenceConfig(),
    val notes: String = "",
    val matchedDriverId: String? = null,
    val rideId: String? = null,        // Linked ride when dispatched
    val dispatchAtEpochMs: Long = scheduledAtEpochMs - 15 * 60 * 1000, // 15min before
) {
    val isPending: Boolean get() = status == ScheduleStatus.PENDING
    val isRecurring: Boolean get() = recurrence.pattern != RecurrencePattern.NONE
    val stopCount: Int get() = stops.size
    val isMultiStop: Boolean get() = stops.size > 2

    val pickup: RideStop? get() = stops.firstOrNull { it.isPickup }
    val dropoff: RideStop? get() = stops.lastOrNull { it.isDropoff }

    val formattedFare: String
        get() = "₡${estimatedFare.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}"
}

// ─── Reminder ───

data class ScheduleReminder(
    val scheduleId: String,
    val type: ReminderType,
    val message: String,
    val triggerAtEpochMs: Long,
)

enum class ReminderType {
    ONE_HOUR,       // 1 hour before
    THIRTY_MIN,     // 30 minutes before
    FIFTEEN_MIN,    // 15 minutes, auto-dispatch starts
    DRIVER_MATCHED, // Driver confirmed
}

// ─── Engine ───

class RideScheduleEngine {

    private val schedules = mutableMapOf<String, ScheduledRide>()
    private val favorites = mutableMapOf<String, FavoriteRoute>()

    // ─── Schedule a Ride ───

    fun scheduleRide(
        userId: String,
        stops: List<RideStop>,
        scheduledAtEpochMs: Long,
        fareMode: RideFareMode = RideFareMode.METERED_TIME_DISTANCE,
        estimatedFare: Long = 0,
        recurrence: RecurrenceConfig = RecurrenceConfig(),
        notes: String = "",
    ): ScheduledRide? {
        if (stops.size < 2) return null
        if (scheduledAtEpochMs <= System.currentTimeMillis()) return null

        val ride = ScheduledRide(
            scheduleId = "sched-${System.currentTimeMillis()}",
            userId = userId,
            stops = stops,
            scheduledAtEpochMs = scheduledAtEpochMs,
            fareMode = fareMode,
            estimatedFare = estimatedFare,
            recurrence = recurrence,
            notes = notes,
        )
        schedules[ride.scheduleId] = ride
        return ride
    }

    // ─── Multi-Stop ───

    fun addStop(scheduleId: String, stop: RideStop): Boolean {
        val ride = schedules[scheduleId] ?: return false
        if (!ride.isPending) return false
        schedules[scheduleId] = ride.copy(
            stops = (ride.stops + stop).sortedBy { it.order },
        )
        return true
    }

    fun removeStop(scheduleId: String, stopId: String): Boolean {
        val ride = schedules[scheduleId] ?: return false
        if (!ride.isPending) return false
        val updated = ride.stops.filter { it.stopId != stopId }
        if (updated.size < 2) return false
        schedules[scheduleId] = ride.copy(stops = updated)
        return true
    }

    // ─── Reminders ───

    fun generateReminders(scheduleId: String): List<ScheduleReminder> {
        val ride = schedules[scheduleId] ?: return emptyList()
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

    fun dispatch(scheduleId: String): ScheduledRide? {
        val ride = schedules[scheduleId] ?: return null
        if (!ride.isPending) return null
        val updated = ride.copy(status = ScheduleStatus.DISPATCHING)
        schedules[scheduleId] = updated
        return updated
    }

    fun assignDriver(scheduleId: String, driverId: String, rideId: String): ScheduledRide? {
        val ride = schedules[scheduleId] ?: return null
        if (ride.status != ScheduleStatus.DISPATCHING) return null
        val updated = ride.copy(
            status = ScheduleStatus.DRIVER_MATCHED,
            matchedDriverId = driverId,
            rideId = rideId,
        )
        schedules[scheduleId] = updated
        return updated
    }

    // ─── Cancel ───

    fun cancel(scheduleId: String): Boolean {
        val ride = schedules[scheduleId] ?: return false
        if (ride.status in listOf(ScheduleStatus.COMPLETED, ScheduleStatus.CANCELLED)) return false
        schedules[scheduleId] = ride.copy(status = ScheduleStatus.CANCELLED)
        return true
    }

    // ─── Favorites ───

    fun saveFavoriteRoute(label: String, icon: String, stops: List<RideStop>): FavoriteRoute {
        val route = FavoriteRoute(
            routeId = "fav-${System.currentTimeMillis()}",
            label = label,
            icon = icon,
            stops = stops,
        )
        favorites[route.routeId] = route
        return route
    }

    fun bookFromFavorite(routeId: String, userId: String, scheduledAt: Long): ScheduledRide? {
        val fav = favorites[routeId] ?: return null
        favorites[routeId] = fav.copy(
            usageCount = fav.usageCount + 1,
            lastUsedEpochMs = System.currentTimeMillis(),
        )
        return scheduleRide(userId, fav.stops, scheduledAt, fav.fareMode)
    }

    fun getFavorites(): List<FavoriteRoute> = favorites.values
        .sortedByDescending { it.usageCount }

    // ─── Queries ───

    fun getUpcoming(userId: String): List<ScheduledRide> = schedules.values
        .filter { it.userId == userId && it.isPending }
        .sortedBy { it.scheduledAtEpochMs }

    fun getSchedule(id: String): ScheduledRide? = schedules[id]

    val totalScheduled: Int get() = schedules.size
}
