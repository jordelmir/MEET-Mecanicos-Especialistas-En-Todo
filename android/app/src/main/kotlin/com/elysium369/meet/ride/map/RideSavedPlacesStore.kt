package com.elysium369.meet.ride.map

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
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

/** Namespace private device data by authenticated principal; never adopt ownerless legacy data. */
fun ridePrivatePreferenceKey(ownerUserId: String?, field: String): String? {
    val owner = ownerUserId?.takeIf { it.isNotBlank() } ?: return null
    val digest = MessageDigest.getInstance("SHA-256").digest(owner.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return "account:$digest:$field"
}

class RideSavedPlacesStore internal constructor(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("ride_saved_places", Context.MODE_PRIVATE),
    )
    private val json = Json { ignoreUnknownKeys = true }

    fun load(ownerUserId: String?): List<RideSavedPlace> {
        val key = ridePrivatePreferenceKey(ownerUserId, KEY) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<RideSavedPlace>>(
                preferences.getString(key, "[]").orEmpty(),
            )
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(ownerUserId: String?, place: RideSavedPlace): List<RideSavedPlace> {
        val key = requireNotNull(ridePrivatePreferenceKey(ownerUserId, KEY)) {
            "Se requiere una cuenta para guardar lugares"
        }
        require(place.latitude.isFinite() && place.latitude in -90.0..90.0)
        require(place.longitude.isFinite() && place.longitude in -180.0..180.0)
        val next = (load(ownerUserId).filterNot { it.slot == place.slot } + place)
            .sortedBy(RideSavedPlace::slot)
        preferences.edit().putString(key, json.encodeToString(next)).apply()
        return next
    }

    private companion object {
        const val KEY = "places"
    }
}
