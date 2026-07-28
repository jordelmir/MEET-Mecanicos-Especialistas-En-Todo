package com.elysium369.meet.ride.map

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RideSavedPlace(
    val slot: String,
    val label: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val providerId: String,
)

class RideSavedPlacesStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("ride_saved_places", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<RideSavedPlace> =
        runCatching {
            json.decodeFromString<List<RideSavedPlace>>(
                preferences.getString(KEY, "[]").orEmpty(),
            )
        }.getOrDefault(emptyList())

    fun save(place: RideSavedPlace): List<RideSavedPlace> {
        val next = (load().filterNot { it.slot == place.slot } + place)
            .sortedBy(RideSavedPlace::slot)
        preferences.edit().putString(KEY, json.encodeToString(next)).apply()
        return next
    }

    private companion object {
        const val KEY = "places"
    }
}
