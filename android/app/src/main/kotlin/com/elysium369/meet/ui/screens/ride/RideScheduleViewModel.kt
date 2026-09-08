package com.elysium369.meet.ui.screens.ride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ride.schedule.FavoriteRoute
import com.elysium369.meet.ride.schedule.RecurrenceConfig
import com.elysium369.meet.ride.schedule.RideScheduleEngine
import com.elysium369.meet.ride.schedule.RideStop
import com.elysium369.meet.ride.schedule.ScheduleReminder
import com.elysium369.meet.ride.schedule.ScheduledRide
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RideScheduleViewModel @Inject constructor(
    private val scheduleEngine: RideScheduleEngine,
) : ViewModel() {

    private val _upcomingRides = MutableStateFlow<List<ScheduledRide>>(emptyList())
    val upcomingRides: StateFlow<List<ScheduledRide>> = _upcomingRides.asStateFlow()

    private val _favoriteRoutes = MutableStateFlow<List<FavoriteRoute>>(emptyList())
    val favoriteRoutes: StateFlow<List<FavoriteRoute>> = _favoriteRoutes.asStateFlow()

    private val _selectedSchedule = MutableStateFlow<ScheduledRide?>(null)
    val selectedSchedule: StateFlow<ScheduledRide?> = _selectedSchedule.asStateFlow()

    private val _reminders = MutableStateFlow<List<ScheduleReminder>>(emptyList())
    val reminders: StateFlow<List<ScheduleReminder>> = _reminders.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Load will be triggered when we have a userId
        }
    }

    fun loadUpcoming(userId: String) {
        viewModelScope.launch {
            val rides = scheduleEngine.getUpcoming(userId)
            _upcomingRides.value = rides
        }
    }

    fun loadFavorites() {
        viewModelScope.launch {
            val favs = scheduleEngine.getFavorites()
            _favoriteRoutes.value = favs
        }
    }

    fun scheduleRide(
        userId: String,
        pickup: RideStop,
        dropoff: RideStop,
        scheduledAtEpochMs: Long,
        fareMode: RideFareMode = RideFareMode.METERED_TIME_DISTANCE,
        estimatedFare: Long = 0,
        recurrence: RecurrenceConfig = RecurrenceConfig(),
        notes: String = "",
    ) {
        viewModelScope.launch {
            val ride = scheduleEngine.scheduleRide(
                userId = userId,
                stops = listOf(pickup, dropoff),
                scheduledAtEpochMs = scheduledAtEpochMs,
                fareMode = fareMode,
                estimatedFare = estimatedFare,
                recurrence = recurrence,
                notes = notes,
            )
            if (ride != null) {
                _feedback.value = "Viaje programado para ${ride.formattedFare}"
                loadUpcoming(userId)
            } else {
                _feedback.value = "No se pudo programar el viaje"
            }
        }
    }

    fun cancelRide(scheduleId: String, userId: String) {
        viewModelScope.launch {
            val cancelled = scheduleEngine.cancel(scheduleId)
            if (cancelled) {
                _feedback.value = "Viaje cancelado"
                loadUpcoming(userId)
            }
        }
    }

    fun loadReminders(scheduleId: String) {
        viewModelScope.launch {
            val reminders = scheduleEngine.generateReminders(scheduleId)
            _reminders.value = reminders
        }
    }

    fun saveFavorite(label: String, stops: List<RideStop>) {
        viewModelScope.launch {
            val fav = scheduleEngine.saveFavoriteRoute(label, "\uD83D\uDE97", stops)
            loadFavorites()
            _feedback.value = "Ruta favorita guardada: ${fav.label}"
        }
    }

    fun dismissFeedback() {
        _feedback.value = null
    }
}
