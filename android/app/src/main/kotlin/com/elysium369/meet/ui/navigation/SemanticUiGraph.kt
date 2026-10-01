package com.elysium369.meet.ui.navigation

import java.util.ArrayDeque

/**
 * ══════════════════════════════════════════════════════════════════════
 *  S E M A N T I C   U I   G R A P H
 *  ──────────────────────────────────────────────────────────────
 *  Governed by: ELYSIUM MASTER IMPLEMENTATION ORDER — ASCENSION MAXIMA §13
 *
 *  Authoritative graph of screens, surfaces, and deterministic navigation paths.
 *  - Models every visible screen across all 5 Product Universes.
 *  - Resolves natural language user intents ("entra a servicios", "abrir partes")
 *    into concrete ScreenNode destinations.
 *  - Calculates deterministic shortest paths using Breadth-First Search (BFS).
 * ══════════════════════════════════════════════════════════════════════
 */
data class ScreenNode(
    val id: String,
    val route: String,
    val universe: ProductUniverse,
    val displayName: String,
    val aliases: Set<String> = emptySet(),
    val availableActions: List<String> = emptyList(),
    val parentRoute: String? = null,
    val requiresAuth: Boolean = false,
)

data class ScreenEdge(
    val fromRoute: String,
    val toRoute: String,
    val trigger: String,
    val description: String = "",
)

class SemanticUiGraph private constructor(
    val nodes: Map<String, ScreenNode>,
    val edges: List<ScreenEdge>,
) {
    private val adjacency: Map<String, List<ScreenEdge>> = edges.groupBy { it.fromRoute }

    /**
     * Resolves a voice or text query into a target ScreenNode using fuzzy alias matching.
     */
    fun resolveTargetScreen(query: String): ScreenNode? {
        val normalized = com.elysium369.meet.core.agent.ui.UiTextNormalizer.normalize(query)
        if (normalized.isBlank()) return null
        val prefix = Regex("""^(?:por favor[,\s]+)?(?:entra|ingresa|abre|abrir|ve|ir|vamos|muestra|ver|navega|llevame)(?:\s+a|\s+en)?(?:\s+la)?(?:\s+seccion)?(?:\s+de)?\s+""")
        val navigationRequest = prefix.containsMatchIn(normalized)
        val target = normalized.replaceFirst(prefix, "").trim()
        if (target.isBlank()) return null
        nodes[target]?.let { return it }
        val labels = nodes.values.filter {
            com.elysium369.meet.core.agent.ui.UiTextNormalizer.normalize(it.displayName) == target
        }
        if (labels.isNotEmpty()) return labels.singleOrNull()
        val aliases = nodes.values.filter { node -> node.aliases.any {
            com.elysium369.meet.core.agent.ui.UiTextNormalizer.normalize(it) == target
        } }
        if (aliases.isNotEmpty()) return aliases.singleOrNull()
        if (!navigationRequest) return null
        // Only explicit navigation may use partial names, and only with one destination.
        val candidates = nodes.values.filter { node ->
            (listOf(node.displayName) + node.aliases).any {
                com.elysium369.meet.core.agent.ui.UiTextNormalizer.normalize(it).contains(target)
            }
        }
        return candidates.singleOrNull()
    }

    /**
     * Finds the shortest navigation sequence from current route to target route using BFS.
     */
    fun findPath(fromRoute: String, toRoute: String): List<ScreenEdge> {
        if (fromRoute == toRoute) return emptyList()
        if (!nodes.containsKey(fromRoute) || !nodes.containsKey(toRoute)) return emptyList()

        val queue = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        val parentEdge = mutableMapOf<String, ScreenEdge>()

        queue.add(fromRoute)
        visited.add(fromRoute)

        while (queue.isNotEmpty()) {
            val curr = queue.poll()
            if (curr == toRoute) {
                // Reconstruct path
                val path = mutableListOf<ScreenEdge>()
                var step = toRoute
                while (step != fromRoute) {
                    val edge = parentEdge[step] ?: break
                    path.add(0, edge)
                    step = edge.fromRoute
                }
                return path
            }

            for (edge in adjacency[curr].orEmpty()) {
                if (edge.toRoute !in visited) {
                    visited.add(edge.toRoute)
                    parentEdge[edge.toRoute] = edge
                    queue.add(edge.toRoute)
                }
            }
        }

        return emptyList()
    }

    fun listScreensInUniverse(universe: ProductUniverse): List<ScreenNode> {
        return nodes.values.filter { it.universe == universe }
    }

    companion object {
        val instance: SemanticUiGraph by lazy { buildDefaultGraph() }
        fun createDefault(): SemanticUiGraph = instance

        private fun buildDefaultGraph(): SemanticUiGraph {
            val n = mutableMapOf<String, ScreenNode>()
            fun addNode(
                id: String,
                route: String,
                universe: ProductUniverse,
                displayName: String,
                aliases: Set<String> = emptySet(),
                actions: List<String> = emptyList(),
                parentRoute: String? = null,
                requiresAuth: Boolean = false,
            ) {
                n[route] = ScreenNode(id, route, universe, displayName, aliases, actions, parentRoute, requiresAuth)
            }

            // Home / Central
            addNode("home", MeetDestinations.HOME, ProductUniverse.MY_VEHICLE, "Inicio", setOf("casa", "principal", "dashboard"))

            // MY_VEHICLE
            addNode("garage", MeetDestinations.GARAGE, ProductUniverse.MY_VEHICLE, "Garaje", setOf("mi garaje", "mis autos", "vehiculos"))
            addNode("health_score", MeetDestinations.HEALTH_SCORE, ProductUniverse.MY_VEHICLE, "Salud del Vehículo", setOf("salud", "score", "bateria", "estado"))
            addNode("dna", MeetDestinations.DNA, ProductUniverse.MY_VEHICLE, "ADN Vehicular", setOf("dna", "historial vehicular", "passport", "pasaporte"))
            addNode("maintenance", MeetDestinations.MAINTENANCE, ProductUniverse.MY_VEHICLE, "Mantenimiento", setOf("servicios preventivos", "mantenimientos"))
            addNode("scanner", MeetDestinations.SCANNER, ProductUniverse.MY_VEHICLE, "Escáner OBD", setOf("escaner", "obd", "escanear", "diagnostico", "check engine"))
            addNode("dtc", MeetDestinations.DTCS, ProductUniverse.MY_VEHICLE, "Códigos DTC", setOf("fallas", "dtcs", "codigos de error"))

            // RESOLVE
            addNode("services", MeetDestinations.ELYSIUM_SERVICES, ProductUniverse.RESOLVE, "Servicios", setOf("servicios generales", "oficios"))
            addNode("services_active", MeetDestinations.SERVICES_ACTIVE, ProductUniverse.RESOLVE, "Servicios Activos", setOf("trabajos activos", "solicitudes activas"))
            addNode("services_completed", MeetDestinations.SERVICES_COMPLETED, ProductUniverse.RESOLVE, "Historial de Servicios", setOf("servicios completados", "historial trabajos"))
            addNode("provider_config", MeetDestinations.PROVIDER_SERVICES_CONFIG, ProductUniverse.RESOLVE, "Configurar Servicios", setOf("mis servicios ofertados", "catalogo proveedor"))
            addNode("mechanics", MeetDestinations.MECHANIC_SERVICES, ProductUniverse.RESOLVE, "Red de Talleres", setOf("talleres", "mecanicos", "talleres mecanicos"))
            addNode("tow_truck", MeetDestinations.TOW_TRUCK, ProductUniverse.RESOLVE, "Grúas", setOf("grua", "remolque", "asistencia vial"))
            addNode("marketplace", MeetDestinations.PARTS_STORE, ProductUniverse.RESOLVE, "Repuestos y Partes", setOf("partes", "tienda", "repuestos", "tienda de partes"))

            // DRIVE
            addNode("hud", MeetDestinations.HUD, ProductUniverse.DRIVE, "HUD Cockpit", setOf("hud", "head up display", "pantalla de conduccion"))
            addNode("trips", MeetDestinations.TRIP_LOG, ProductUniverse.DRIVE, "Registro de Viajes", setOf("viajes conducidos", "bitacora"))

            // MOBILITY
            addNode("rides", MeetDestinations.RIDE_HOME, ProductUniverse.MOBILITY, "Viajes y Rides", setOf("pedir viaje", "viajar", "ride", "taxi", "transporte"))
            addNode("ride_driver", MeetDestinations.RIDE_DRIVER_REGISTRATION, ProductUniverse.MOBILITY, "Modo Conductor", setOf("conductor", "manejar", "modo chofer"))

            // PROFESSIONAL
            addNode("pro_hub", MeetDestinations.PRO_HUB, ProductUniverse.PROFESSIONAL, "Centro Pro", setOf("pro", "profesional", "taller pro"))
            addNode("terminal", MeetDestinations.TERMINAL, ProductUniverse.PROFESSIONAL, "Terminal OBD", setOf("consola", "comandos at", "raw obd"))

            // ELYSIUM_AI
            addNode("ai", MeetDestinations.AI, ProductUniverse.ELYSIUM_AI, "Elysium AI Copilot", setOf("asistente", "copiloto", "agente", "laya"))
            addNode("agent_store", MeetDestinations.AGENT_STORE, ProductUniverse.ELYSIUM_AI, "Tienda de Agentes", setOf("tienda de agentes", "comprar agentes", "archetypes", "catalogo de agentes"))

            // SAFETY
            addNode("messages", MeetDestinations.MESSAGES, ProductUniverse.ELYSIUM_AI, "Mensajes", setOf("chat", "conversaciones", "mensajería", "mensajeria", "mensajes y llamadas"), requiresAuth = true)
            addNode("safety", MeetDestinations.SAFETY_HOME, ProductUniverse.SAFETY, "Seguridad Ciudadana", setOf("seguridad", "sos", "alertas"))
            addNode("safety_map", MeetDestinations.SAFETY_MAP, ProductUniverse.SAFETY, "Mapa de Seguridad", setOf("mapa de seguridad", "zonas seguras"))

            val e = mutableListOf<ScreenEdge>()
            fun addEdge(from: String, to: String, trigger: String, desc: String = "") {
                e.add(ScreenEdge(from, to, trigger, desc))
            }

            // Universal edges from Home to top-level sections
            val topRoutes = listOf(
                MeetDestinations.MESSAGES,
                MeetDestinations.GARAGE,
                MeetDestinations.SCANNER,
                MeetDestinations.ELYSIUM_SERVICES,
                MeetDestinations.MECHANIC_SERVICES,
                MeetDestinations.PARTS_STORE,
                MeetDestinations.RIDE_HOME,
                MeetDestinations.PRO_HUB,
                MeetDestinations.AI,
                MeetDestinations.AGENT_STORE,
                MeetDestinations.SAFETY_HOME,
            )

            topRoutes.forEach { route ->
                addEdge(MeetDestinations.HOME, route, "navigate_to_$route")
                addEdge(route, MeetDestinations.HOME, "back_to_home")
            }

            // Services sub-navigation
            addEdge(MeetDestinations.ELYSIUM_SERVICES, MeetDestinations.SERVICES_ACTIVE, "view_active_services")
            addEdge(MeetDestinations.SERVICES_ACTIVE, MeetDestinations.ELYSIUM_SERVICES, "back_to_services")
            addEdge(MeetDestinations.ELYSIUM_SERVICES, MeetDestinations.SERVICES_COMPLETED, "view_completed_services")
            addEdge(MeetDestinations.SERVICES_COMPLETED, MeetDestinations.ELYSIUM_SERVICES, "back_to_services")
            addEdge(MeetDestinations.ELYSIUM_SERVICES, MeetDestinations.PROVIDER_SERVICES_CONFIG, "configure_provider_catalog")
            addEdge(MeetDestinations.PROVIDER_SERVICES_CONFIG, MeetDestinations.ELYSIUM_SERVICES, "back_to_services")

            // OBD & Diagnostic sub-navigation
            addEdge(MeetDestinations.SCANNER, MeetDestinations.DTCS, "view_dtcs")
            addEdge(MeetDestinations.DTCS, MeetDestinations.SCANNER, "back_to_scanner")
            addEdge(MeetDestinations.GARAGE, MeetDestinations.HEALTH_SCORE, "view_health")
            addEdge(MeetDestinations.GARAGE, MeetDestinations.DNA, "view_dna")

            // AI & Store sub-navigation
            addEdge(MeetDestinations.AI, MeetDestinations.AGENT_STORE, "open_agent_store")
            addEdge(MeetDestinations.AGENT_STORE, MeetDestinations.AI, "back_to_ai")

            return SemanticUiGraph(nodes = n, edges = e)
        }
    }
}
