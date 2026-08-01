package com.elysium369.meet.ride.map

import android.content.Context

enum class RideDriverAvatar(
    val storageId: String,
    val displayName: String,
    val description: String,
) {
    CRIMSON_DRAGON("crimson_dragon", "Dragón carmesí", "Fuego rojo Elysium"),
    CYBER_WYVERN("cyber_wyvern", "Wyvern cibernético", "Alas cian de alta visibilidad"),
    OBSIDIAN_PHOENIX("obsidian_phoenix", "Fénix obsidiana", "Renacer ámbar y grafito"),
    TURBO_RONIN("turbo_ronin", "Ronin turbo", "Casco neón de carretera"),
    ;

    companion object {
        fun fromStorage(value: String?): RideDriverAvatar =
            entries.firstOrNull { it.storageId == value } ?: CRIMSON_DRAGON
    }
}

enum class RidePassengerAvatar(
    val storageId: String,
    val displayName: String,
    val description: String,
) {
    NEON_PERSON("neon_person", "Persona neón", "Silueta humana clara"),
    CITY_EXPLORER("city_explorer", "Explorador urbano", "Viajero con visor luminoso"),
    AURA_HERO("aura_hero", "Héroe de aura", "Guerrero de energía original"),
    VANGUARD_GUARDIAN("vanguard_guardian", "Guardián Vanguard", "Protector futurista propietario"),
    ;

    companion object {
        fun fromStorage(value: String?): RidePassengerAvatar =
            entries.firstOrNull { it.storageId == value } ?: NEON_PERSON
    }
}

data class RideMapAvatarSelection(
    val driver: RideDriverAvatar = RideDriverAvatar.CRIMSON_DRAGON,
    val passenger: RidePassengerAvatar = RidePassengerAvatar.NEON_PERSON,
)

class RideMapAvatarStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): RideMapAvatarSelection = RideMapAvatarSelection(
        driver = RideDriverAvatar.fromStorage(preferences.getString(KEY_DRIVER, null)),
        passenger = RidePassengerAvatar.fromStorage(preferences.getString(KEY_PASSENGER, null)),
    )

    fun save(selection: RideMapAvatarSelection) {
        preferences.edit()
            .putString(KEY_DRIVER, selection.driver.storageId)
            .putString(KEY_PASSENGER, selection.passenger.storageId)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "elysium_ride_map_avatars"
        private const val KEY_DRIVER = "driver_avatar"
        private const val KEY_PASSENGER = "passenger_avatar"
    }
}
