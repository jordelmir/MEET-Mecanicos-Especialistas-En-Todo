package com.elysium369.meet.data.local

import androidx.room.TypeConverter
import java.util.UUID

class Converters {
    @TypeConverter
    fun fromUUID(uuid: UUID?): String? {
        return uuid?.toString()
    }

    @TypeConverter
    fun toUUID(uuidString: String?): UUID? {
        return uuidString?.let { UUID.fromString(it) }
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.joinToString(";;")
    }

    @TypeConverter
    fun toStringList(string: String?): List<String>? {
        return string?.split(";;")?.filter { it.isNotEmpty() }
    }
}
