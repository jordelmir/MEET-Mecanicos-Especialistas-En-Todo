package com.elysium.nexus.fabric.session

import com.elysium.nexus.fabric.canonical.Protocol

/**
 * The §4.6 permission gate.
 *
 * Before a [ControlSession] can activate a route,
 * the gate checks that the app holds every Android
 * permission the route's protocol requires.
 *
 * The gate is **pure logic**: it does not request
 * permissions (that's the UI's job). It only answers
 * "do we have what we need?" and "what's missing?".
 *
 * ## Example
 *
 * ```kotlin
 * val result = PermissionGate.check(
 *     protocol = Protocol.DirectIr,
 *     grantedPermissions = setOf("android.permission.TRANSMIT_IR")
 * )
 * // result == PermissionResult.Granted
 * ```
 */
object PermissionGate {

    /**
     * Check whether [grantedPermissions] covers every
     * permission required by [protocol].
     */
    fun check(
        protocol: Protocol,
        grantedPermissions: Set<String>
    ): PermissionResult {
        val required = requiredPermissions(protocol)
        if (required.isEmpty()) return PermissionResult.Granted

        val missing = required - grantedPermissions
        if (missing.isEmpty()) return PermissionResult.Granted

        // Check if any missing permission needs rationale
        val rationaleNeeded = missing.firstOrNull { it in RATIONALE_PERMISSIONS }
        if (rationaleNeeded != null) {
            return PermissionResult.RationaleRequired(
                permission = rationaleNeeded,
                rationale = rationaleText(rationaleNeeded)
            )
        }

        return PermissionResult.Denied(missing = missing.toList())
    }

    /**
     * The set of Android permissions required per protocol.
     * Empty means no runtime permission is needed.
     */
    fun requiredPermissions(protocol: Protocol): Set<String> = when (protocol) {
        Protocol.DirectIr -> setOf(TRANSMIT_IR)
        Protocol.HubIr -> emptySet() // hub proxies the IR

        Protocol.HidOverBle -> setOf(BLUETOOTH_CONNECT, BLUETOOTH_SCAN)
        Protocol.HidOverBluetooth -> setOf(BLUETOOTH_CONNECT, BLUETOOTH_SCAN)
        Protocol.HidOverUsb -> emptySet() // USB accessory, no runtime perm

        Protocol.Ble -> setOf(BLUETOOTH_CONNECT, BLUETOOTH_SCAN)

        Protocol.WiFi -> setOf(ACCESS_FINE_LOCATION) // Wi-Fi scan needs location
        Protocol.Ethernet -> emptySet()

        Protocol.Matter -> setOf(BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_FINE_LOCATION)
        Protocol.Thread -> setOf(BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_FINE_LOCATION)

        Protocol.Zigbee -> emptySet() // via hub
        Protocol.ZWave -> emptySet()
        Protocol.ZWaveLongRange -> emptySet()

        Protocol.Mqtt -> setOf(INTERNET)
        Protocol.Onvif -> setOf(INTERNET)
        Protocol.Rtsp -> setOf(INTERNET)
        Protocol.Rtsps -> setOf(INTERNET)
        Protocol.WebRtc -> setOf(INTERNET, RECORD_AUDIO) // two-way voice
        Protocol.HdmiCec -> emptySet()

        Protocol.VendorRest -> setOf(INTERNET)
        Protocol.VendorWebSocket -> setOf(INTERNET)
        Protocol.ElysiumLink -> setOf(INTERNET)

        Protocol.Unknown -> emptySet()
    }

    // ── Well-known Android permissions ──────────────────

    const val TRANSMIT_IR = "android.permission.TRANSMIT_IR"
    const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val INTERNET = "android.permission.INTERNET"
    const val RECORD_AUDIO = "android.permission.RECORD_AUDIO"

    /** Permissions that need user-facing rationale before requesting. */
    private val RATIONALE_PERMISSIONS = setOf(
        ACCESS_FINE_LOCATION,
        RECORD_AUDIO,
        BLUETOOTH_CONNECT,
        BLUETOOTH_SCAN
    )

    private fun rationaleText(permission: String): String = when (permission) {
        ACCESS_FINE_LOCATION ->
            "Location access is required to scan for Wi-Fi and Thread devices nearby."
        RECORD_AUDIO ->
            "Microphone access is required for two-way voice with cameras."
        BLUETOOTH_CONNECT ->
            "Bluetooth connection permission is required to communicate with BLE/HID devices."
        BLUETOOTH_SCAN ->
            "Bluetooth scan permission is required to discover nearby devices."
        else -> "This permission is required for the selected transport."
    }
}

/**
 * The result of a permission gate check.
 */
sealed class PermissionResult {
    /** All required permissions are granted. */
    data object Granted : PermissionResult()

    /**
     * One or more permissions are missing.
     * The UI should request them.
     */
    data class Denied(val missing: List<String>) : PermissionResult()

    /**
     * A permission needs a user-facing rationale
     * before requesting. Show the rationale first,
     * then request.
     */
    data class RationaleRequired(
        val permission: String,
        val rationale: String
    ) : PermissionResult()
}
