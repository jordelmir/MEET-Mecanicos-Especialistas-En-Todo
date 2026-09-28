package com.elysium.nexus.fabric.rooms

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceTwin

/**
 * §46 Device Rooms — Home/Room/Zone hierarchy.
 *
 * Organizes devices into a spatial hierarchy:
 * ```
 * Home
 * └── Floor
 *     └── Room
 *         └── Zone
 *             └── Device
 * ```
 *
 * The hierarchy powers:
 * - Room-level control ("turn off living room")
 * - Zone-based automation
 * - Device proximity detection
 * - Scene scoping
 */
data class DeviceHome(
    val id: String,
    val name: String,
    val floors: List<DeviceFloor> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis()
) {
    val allDevices: List<DeviceId>
        get() = floors.flatMap { floor ->
            floor.rooms.flatMap { room ->
                room.zones.flatMap { zone ->
                    zone.deviceIds
                }
            }
        }

    fun findDevice(deviceId: DeviceId): DeviceLocation? {
        for (floor in floors) {
            for (room in floor.rooms) {
                for (zone in room.zones) {
                    if (deviceId in zone.deviceIds) {
                        return DeviceLocation(
                            home = this,
                            floor = floor,
                            room = room,
                            zone = zone
                        )
                    }
                }
            }
        }
        return null
    }

    fun devicesInRoom(roomId: String): List<DeviceId> {
        return floors.flatMap { floor ->
            floor.rooms.filter { it.id == roomId }.flatMap { room ->
                room.zones.flatMap { it.deviceIds }
            }
        }
    }

    fun roomsWithDevices(): List<RoomWithDevices> {
        return floors.flatMap { floor ->
            floor.rooms.map { room ->
                val devices = room.zones.flatMap { it.deviceIds }
                RoomWithDevices(
                    floorName = floor.name,
                    room = room,
                    deviceCount = devices.size
                )
            }
        }
    }
}

data class DeviceFloor(
    val id: String,
    val name: String,
    val rooms: List<DeviceRoom> = emptyList()
)

data class DeviceRoom(
    val id: String,
    val name: String,
    val zones: List<DeviceZone> = emptyList()
)

data class DeviceZone(
    val id: String,
    val name: String,
    val deviceIds: List<DeviceId> = emptyList()
)

data class DeviceLocation(
    val home: DeviceHome,
    val floor: DeviceFloor,
    val room: DeviceRoom,
    val zone: DeviceZone
)

data class RoomWithDevices(
    val floorName: String,
    val room: DeviceRoom,
    val deviceCount: Int
)
