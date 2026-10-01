package com.elysium.nexus.databases.pairing

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a paired or discovered universal device (Smart TV, PC, Mac, IR remote, BLE host).
 *
 * Identity rules (§9 V06):
 * - `stableIdentity` must NEVER be an IP address or display name.
 *   Preferred: UPnP UDN, serial, MAC, vendor device ID, deterministic composite.
 */
@Entity(
    tableName = "paired_devices",
    indices = [Index("stable_identity")]
)
data class PairedDeviceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "brand")
    val brand: String,

    @ColumnInfo(name = "device_type")
    val deviceType: String, // "TV", "MEDIA_PLAYER", "DESKTOP_MAC", "DESKTOP_WIN", "GAMEPAD_HOST"

    @ColumnInfo(name = "protocol_type")
    val protocolType: String, // "LG_WEBOS", "SAMSUNG_TIZEN", "ROKU", "ANDROID_TV", "MAC_AGENT", "INFRARED", "BLE_HID"

    @ColumnInfo(name = "ip_address")
    val ipAddress: String? = null,

    @ColumnInfo(name = "port")
    val port: Int? = null,

    @ColumnInfo(name = "mac_address")
    val macAddress: String? = null,

    /** §9: Stable cross-protocol identity. Never the IP. */
    @ColumnInfo(name = "stable_identity")
    val stableIdentity: String? = null,

    @ColumnInfo(name = "pairing_token")
    val pairingToken: String? = null,

    @ColumnInfo(name = "client_key")
    val clientKey: String? = null,

    @ColumnInfo(name = "auth_status")
    val authStatus: String = "UNPAIRED", // "PAIRED", "UNPAIRED", "PIN_REQUIRED", "FAILED"

    @ColumnInfo(name = "last_seen_timestamp")
    val lastSeenTimestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "custom_name")
    val customName: String? = null
)
