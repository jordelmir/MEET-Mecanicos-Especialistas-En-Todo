package com.elysium.nexus.databases.compatibility

import androidx.room.TypeConverter
import com.elysium.nexus.core.compat.CompatibilityStatus

/**
 * Room TypeConverters for the compatibility entity.
 *
 * Room's native types are `Int`, `Long`, `Float`, `Double`,
 * `Boolean`, `String`, and `ByteArray`. Our domain uses
 * `List<String>` (capabilities) and `CompatibilityStatus`
 * (the §33 enum). Both are stored as `String` in the
 * entity; the converter goes the other way (String ↔
 * domain) so the DAO and the repository can speak
 * domain types.
 *
 * ## Why semicolon as the list separator
 *
 * The domain's `List<String>` is a flat list of capability
 * names ("buttons", "sticks", "triggers", "gyro", ...).
 * Storing it as a `String` with a delimiter is the
 * standard "store-an-array-as-a-text-column" pattern
 * for SQLite. We use `;` (semicolon) because no capability
 * name contains a semicolon — `buttons;sticks` parses
 * unambiguously. The delimiter is a private constant; if
 * it ever needs to change, the migration in Phase 1.1+
 * handles the data conversion.
 */
object Converters {

    /** The delimiter used to separate list elements in a text column. */
    private const val LIST_DELIMITER: String = ";"

    @TypeConverter
    @JvmStatic
    fun fromList(list: List<String>?): String =
        list?.joinToString(LIST_DELIMITER) ?: ""

    @TypeConverter
    @JvmStatic
    fun toList(joined: String?): List<String> =
        if (joined.isNullOrEmpty()) emptyList()
        else joined.split(LIST_DELIMITER).filter { it.isNotEmpty() }

    @TypeConverter
    @JvmStatic
    fun fromStatus(status: CompatibilityStatus): String = status.name

    @TypeConverter
    @JvmStatic
    fun toStatus(name: String): CompatibilityStatus =
        CompatibilityStatus.valueOf(name)
}
