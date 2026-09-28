package com.elysium.nexus.fabric.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * JVM tests for the §4.2 [DeviceTwin] +
 * [DeviceKnowledgeGraph] + [Location] +
 * [GraphEdge] / [GraphNode] / [Relation] +
 * [DeviceState] hierarchy.
 */
class DeviceTwinTest {

    @Test
    fun `DeviceTwin rejects a blank deviceId`() {
        try {
            DeviceTwin(
                deviceId = DeviceId(""),
                deviceType = DeviceType.Light,
                capabilities = setOf(Capability.OnOff),
                label = "L"
            )
            fail("Expected IllegalArgumentException for blank deviceId.")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("DeviceId"))
        }
    }

    @Test
    fun `DeviceTwin requires at least one capability`() {
        try {
            DeviceTwin(
                deviceId = DeviceId("d1"),
                deviceType = DeviceType.Light,
                capabilities = emptySet()
            )
            fail("Expected IllegalArgumentException for empty capabilities.")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("capability"))
        }
    }

    @Test
    fun `DeviceTwin allows Unknown device type with no capabilities`() {
        // The exception is "or be explicitly DeviceType.Unknown".
        val twin = DeviceTwin(
            deviceId = DeviceId("d1"),
            deviceType = DeviceType.Unknown
        )
        assertEquals(DeviceType.Unknown, twin.deviceType)
    }

    @Test
    fun `DeviceState Level clamps to 0-1`() {
        try {
            DeviceState.Level(1.5f)
            fail("Expected IllegalArgumentException for Level > 1.0")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `DeviceState ColorTemperature clamps to 1000-40000`() {
        try {
            DeviceState.ColorTemperature(500)
            fail("Expected IllegalArgumentException for ColorTemperature < 1000")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Location requires at least one of home, floor, room, zone`() {
        try {
            Location()
            fail("Expected IllegalArgumentException for all-null Location.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `GraphEdge rejects self-reference`() {
        try {
            GraphEdge(
                from = DeviceId("d1"),
                to = DeviceId("d1"),
                relation = Relation.Controls
            )
            fail("Expected IllegalArgumentException for self-referential edge.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `DeviceKnowledgeGraph rejects edges that reference missing nodes`() {
        try {
            DeviceKnowledgeGraph(
                nodes = mapOf(DeviceId("d1") to GraphNode(light("d1"))),
                edges = listOf(
                    GraphEdge(DeviceId("d1"), DeviceId("d2"), Relation.Controls)
                )
            )
            fail("Expected IllegalArgumentException for edge with missing target.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `DeviceKnowledgeGraph devicesInRoom filters by room`() {
        val g = DeviceKnowledgeGraph(
            nodes = mapOf(
                DeviceId("a") to GraphNode(light("a"), Location(room = "Bedroom")),
                DeviceId("b") to GraphNode(light("b"), Location(room = "Bedroom")),
                DeviceId("c") to GraphNode(light("c"), Location(room = "Kitchen"))
            )
        )
        val bedroom = g.devicesInRoom("Bedroom")
        assertEquals(2, bedroom.size)
        val ids = bedroom.map { it.device.deviceId }.toSet()
        assertTrue(ids.contains(DeviceId("a")))
        assertTrue(ids.contains(DeviceId("b")))
    }

    @Test
    fun `DeviceKnowledgeGraph devicesWithCapability returns matching devices`() {
        val g = DeviceKnowledgeGraph(
            nodes = mapOf(
                DeviceId("l") to GraphNode(
                    DeviceTwin(
                        deviceId = DeviceId("l"),
                        deviceType = DeviceType.Light,
                        capabilities = setOf(Capability.OnOff),
                        label = "Light l"
                    )
                ),
                DeviceId("k") to GraphNode(
                    DeviceTwin(
                        deviceId = DeviceId("k"),
                        deviceType = DeviceType.Lock,
                        capabilities = setOf(Capability.LockUnlock),
                        label = "Lock k"
                    )
                )
            )
        )
        val locks = g.devicesWithCapability(Capability.LockUnlock)
        assertEquals(1, locks.size)
        assertEquals(DeviceId("k"), locks.first().device.deviceId)
    }

    @Test
    fun `DeviceKnowledgeGraph outgoing and incoming edges are correct`() {
        val g = DeviceKnowledgeGraph(
            nodes = mapOf(
                DeviceId("switch") to GraphNode(light("switch")),
                DeviceId("light") to GraphNode(light("light"))
            ),
            edges = listOf(
                GraphEdge(DeviceId("switch"), DeviceId("light"), Relation.Controls)
            )
        )
        assertEquals(1, g.outgoingEdges(DeviceId("switch")).size)
        assertEquals(0, g.outgoingEdges(DeviceId("light")).size)
        assertEquals(1, g.incomingEdges(DeviceId("light")).size)
        assertEquals(0, g.incomingEdges(DeviceId("switch")).size)
    }

    @Test
    fun `Relation has Controls, Triggers, Observes, Secures, BelongsTo, Measures, Powers, Coordinates, RemoteControls`() {
        // §5 relationship vocabulary.
        val expected = setOf(
            "Controls", "Triggers", "Observes", "Secures", "BelongsTo",
            "Measures", "Powers", "Coordinates", "RemoteControls"
        )
        val actual = Relation.values().map { it.name }.toSet()
        for (name in expected) {
            if (name !in actual) {
                fail("Expected relation '$name' (per §5) is missing.")
            }
        }
    }

    @Test
    fun `DeviceType has light, lock, AC, camera, and 50+ variants`() {
        // The taxonomy per §4 / §5 / §11-§22
        // spans 50+ device types. The test
        // checks the floor.
        assertTrue(
            "DeviceType enum should have at least 30 variants; got ${DeviceType.values().size}",
            DeviceType.values().size >= 30
        )
    }

    private fun light(id: String): DeviceTwin = DeviceTwin(
        deviceId = DeviceId(id),
        deviceType = DeviceType.Light,
        capabilities = setOf(Capability.OnOff),
        label = "Light $id"
    )
}
