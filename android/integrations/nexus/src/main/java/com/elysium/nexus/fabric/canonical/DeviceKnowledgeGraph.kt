package com.elysium.nexus.fabric.canonical

/**
 * The §5 Elysium Device Knowledge Graph.
 *
 * The DKG is the system's local, syncable model
 * of the user's home. Every device is a node;
 * the relationships (Switch controls Light,
 * Thermostat controls HVAC, Sensor triggers
 * Alarm, Camera observes Entrance, Lock secures
 * Door, …) are edges. The graph is the substrate
 * the §28 automation engine reasons over.
 *
 * The graph is intentionally **immutable**. Every
 * mutation produces a new graph; the previous
 * graph is the "last known good" the Hub
 * persists in a ring buffer.
 *
 * The graph is **local-first**: the Hub is the
 * canonical store. The Android app and the
 * desktop agents mirror. Cloud sync is
 * end-to-end encrypted and opt-in.
 *
 * ## Why a graph and not a flat list
 *
 * A flat list of devices can express "this light
 * is in the bedroom". A graph can also express
 * "this switch controls this light" + "this
 * sensor triggers this alarm" + "this camera
 * observes this entrance". The §5 relationships
 * are the data that lets an automation reason
 * (e.g. "if any entrance sensor triggers while
 * no one is home, sound the alarm AND start
 * recording the entrance camera").
 */
data class DeviceKnowledgeGraph(
    val nodes: Map<DeviceId, GraphNode> = emptyMap(),
    val edges: List<GraphEdge> = emptyList(),
    /** Wall-clock nanoseconds of the last mutation. */
    val lastMutationNs: Long = 0L
) {
    init {
        require(nodes.values.all { it.device.deviceId in nodes.keys }) {
            "Every GraphNode.device must be keyed in nodes."
        }
        require(edges.all { it.from in nodes.keys && it.to in nodes.keys }) {
            "Every GraphEdge must connect two existing nodes."
        }
        require(lastMutationNs >= 0L) {
            "lastMutationNs must be non-negative."
        }
    }

    /**
     * The number of nodes in the graph. Convenience
     * for log messages and dashboards.
     */
    val nodeCount: Int get() = nodes.size

    /**
     * The number of edges in the graph.
     */
    val edgeCount: Int get() = edges.size

    /**
     * The set of distinct rooms referenced by the
     * graph's nodes. Used to power the room view
     * in the UI.
     */
    val rooms: Set<String> = nodes.values
        .mapNotNull { it.location?.room }
        .toSet()

    /**
     * @return all devices in [room] (case-sensitive
     * exact match). Returns an empty list if the
     * room has no devices.
     */
    fun devicesInRoom(room: String): List<GraphNode> = nodes.values
        .filter { it.location?.room == room }

    /**
     * @return all devices that have at least one
     * capability in [capabilities]. The check is
     * O(n) over the node set; a future optimisation
     * is a per-capability index.
     */
    fun devicesWithCapability(
        vararg capabilities: Capability
    ): List<GraphNode> {
        if (capabilities.isEmpty()) return emptyList()
        val wanted = capabilities.toSet()
        return nodes.values.filter { node ->
            node.device.capabilities.any { it in wanted }
        }
    }

    /**
     * @return all edges originating from [deviceId]
     * (the "outgoing" relationships). Used by the
     * §28 automation engine to walk a "switch
     * controls" chain.
     */
    fun outgoingEdges(deviceId: DeviceId): List<GraphEdge> =
        edges.filter { it.from == deviceId }

    /**
     * @return all edges arriving at [deviceId]
     * (the "incoming" relationships). E.g. for a
     * light, this returns every switch + every
     * motion sensor that controls it.
     */
    fun incomingEdges(deviceId: DeviceId): List<GraphEdge> =
        edges.filter { it.to == deviceId }
}

/**
 * A node in the DKG. A node is the **identity**
 * of a device (id + label + location) plus a
 * [DeviceTwin] (the device's current state). The
 * twin can be replaced; the node identity
 * persists across re-pairing.
 */
data class GraphNode(
    /** The device's full twin (id, type, capabilities, state). */
    val device: DeviceTwin,
    /** The device's location in the home. Optional. */
    val location: Location? = null
) {
    init {
        require(device.label.isNotBlank() || location != null) {
            "GraphNode must have a label or a location."
        }
    }
}

/** A location in the home. A device lives in one room + one zone. */
data class Location(
    val home: String? = null,
    val floor: String? = null,
    val room: String? = null,
    val zone: String? = null
) {
    init {
        // A location with all-null fields is not a
        // location; reject so callers pass at least
        // one of home/floor/room/zone.
        require(home != null || floor != null || room != null || zone != null) {
            "Location must declare at least one of home, floor, room, zone."
        }
    }
}

/**
 * A directed edge in the DKG. The set of
 * [Relation] variants is the §5 vocabulary
 * plus a small set of "infra" relations
 * (POWERS, BELONGS_TO) for completeness.
 *
 * Every edge is typed: the engine inspects
 * the [relation] to decide what to do when
 * the source fires an event. A `CONTROLS`
 * edge means "when the source changes, the
 * target is asked to follow". A `TRIGGERS`
 * edge means "the source's events are routed
 * to the target's automation hooks". A
 * `OBSERVES` edge means "the target watches
 * the source" (a camera watching a door).
 */
data class GraphEdge(
    val from: DeviceId,
    val to: DeviceId,
    val relation: Relation
) {
    init {
        require(from != to) {
            "GraphEdge cannot be self-referential (from == to == $from)."
        }
    }
}

/**
 * The §5 relationship vocabulary. The enum
 * is closed; a new relationship is an ADR.
 * The vocabulary is the substrate the §28
 * automation engine reasons over: a CONTROLS
 * edge is a "follow" relationship, a TRIGGERS
 * edge is a "publish" relationship, an
 * OBSERVES edge is a "subscribe" relationship.
 */
enum class Relation {
    /** `from` controls `to` (switch → light). */
    Controls,
    /** `from` is a controller (remote, app) for `to`. */
    RemoteControls,
    /** `from`'s events trigger `to` (sensor → alarm). */
    Triggers,
    /** `from` observes `to` (camera → entrance). */
    Observes,
    /** `from` secures `to` (lock → door). */
    Secures,
    /** `from` is inside `to` (door → room). */
    BelongsTo,
    /** `from` measures `to` (energy meter → circuit). */
    Measures,
    /** `from` powers `to` (circuit → device). */
    Powers,
    /** `from` coordinates `to` (automation → devices). */
    Coordinates
}
