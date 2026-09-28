package com.elysium.nexus.core.profile

import android.content.Context
import android.content.SharedPreferences

/**
 * Phase ULT.8 — "Last device" memory.
 *
 * Persists the most-recently-connected device
 * (Mac, BT, or TV) so the user can reconnect
 * with one tap from the Hub. The store is
 * intentionally tiny (one slot, plus a
 * secondary "previous" slot for the Mac agent's
 * IP+port).
 *
 * ## Why SharedPreferences and not DataStore
 *
 * The data is a single string-keyed blob; the
 * read happens once on Hub open. The
 * SharedPreferences API is enough and avoids
 * the DataStore coroutine wiring for a non-flow
 * use case.
 *
 * ## Persistence
 *
 * The store is written to the default
 * `SharedPreferences` file under the
 * `last_device` key. The format is a pipe-
 * separated string of fields. The fields are
 * versioned (the first field is a type tag)
 * so the schema can evolve without breaking
 * the parser.
 */
class LastDeviceMemory(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The stored last device, or null if no
     * device has been connected yet.
     */
    fun get(): LastDevice? {
        val raw = prefs.getString(KEY_LAST, null) ?: return null
        return parse(raw)
    }

    /**
     * Persist the given last device. The call
     * is synchronous; it is expected to happen
     * at most once per control session.
     */
    fun set(device: LastDevice) {
        prefs.edit().putString(KEY_LAST, device.serialize()).apply()
    }

    /**
     * Clear the last device. Used when the user
     * explicitly "forgets" the device.
     */
    fun clear() {
        prefs.edit().remove(KEY_LAST).apply()
    }

    private fun parse(raw: String): LastDevice? = LastDevice.parse(raw)

    companion object {
        private const val PREFS_NAME = "elysium_last_device"
        private const val KEY_LAST = "last"
    }
}

/**
 * A snapshot of the most-recently-connected
 * device. Three variants:
 *
 *  - [Mac] — a Mac/PC with the Elysium agent
 *    running (Wi-Fi, Elysium Link). The store
 *    remembers the IP and port; on reconnect the
 *    user types the PIN again (the Mac agent
 *    generates a fresh PIN each time).
 *  - [Bluetooth] — a Bluetooth HID host (any
 *    device that accepts Bluetooth keyboard +
 *    mouse input).
 */
sealed class LastDevice {
    abstract val name: String

    fun serialize(): String = when (this) {
        is Mac -> "mac|$name|$host|$port"
        is Bluetooth -> "bt|$name|$address"
    }

    data class Mac(
        override val name: String,
        val host: String,
        val port: Int
    ) : LastDevice()

    data class Bluetooth(
        override val name: String,
        val address: String
    ) : LastDevice()

    companion object {
        /**
         * Parse a serialised [LastDevice]. Returns
         * `null` on malformed input. The parser
         * never throws.
         */
        fun parse(raw: String): LastDevice? {
            val fields = raw.split("|")
            return when (fields.getOrNull(0)) {
                "mac" -> {
                    val name = fields.getOrNull(1) ?: return null
                    val host = fields.getOrNull(2) ?: return null
                    val port = fields.getOrNull(3)?.toIntOrNull() ?: 7878
                    Mac(name = name, host = host, port = port)
                }
                "bt" -> {
                    val name = fields.getOrNull(1) ?: return null
                    val address = fields.getOrNull(2) ?: return null
                    Bluetooth(name = name, address = address)
                }
                else -> null
            }
        }
    }
}
