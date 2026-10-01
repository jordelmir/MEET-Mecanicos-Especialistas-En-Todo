package com.elysium369.meet.core.agent.ui

import com.elysium369.meet.ui.navigation.MeetDestinations

/**
 * Entrada canónica en el catálogo de navegación de la aplicación.
 */
data class NavigationEntry(
    val route: String,
    val label: String,
    val aliases: Set<String> = emptySet(),
    val icon: String = "🧭",
)

/**
 * Catálogo canónico de rutas de primer nivel para la resolución del comando "SECCIÓN <texto>".
 * Si el usuario dice "Sección Scanner" o "Entra a la sección Viajes" desde una pantalla
 * donde el botón no esté compuesto, este catálogo proporciona la ruta canónica segura.
 */
object NavigationCatalog {

    val entries: List<NavigationEntry> = listOf(
        NavigationEntry(MeetDestinations.MESSAGES, "Mensajes", setOf("chat", "conversaciones", "mensajería", "mensajeria", "mensajes y llamadas"), "💬"),
        NavigationEntry(
            route = MeetDestinations.HOME,
            label = "Inicio",
            aliases = setOf("home", "principal", "pantalla principal", "menú principal"),
            icon = "🏠"
        ),
        NavigationEntry(
            route = MeetDestinations.SCANNER,
            label = "Scanner",
            aliases = setOf("escáner", "escaner", "diagnóstico", "diagnostico", "obd", "obd2", "escanear"),
            icon = "🔍"
        ),
        NavigationEntry(
            route = MeetDestinations.RIDE_HOME,
            label = "Viajes",
            aliases = setOf("viaje", "rides", "solicitar viaje", "pedir viaje", "transporte", "chofer", "movilidad"),
            icon = "🚗"
        ),
        NavigationEntry(
            route = MeetDestinations.GARAGE,
            label = "Garage",
            aliases = setOf("garaje", "mi vehículo", "mi vehiculo", "mi carro", "autos", "carros"),
            icon = "🏎️"
        ),
        NavigationEntry(
            route = MeetDestinations.ELYSIUM_SERVICES,
            label = "Servicios",
            aliases = setOf("servicios activos", "marketplace", "especialistas", "taller", "fontanero", "electricista"),
            icon = "🛠️"
        ),
        NavigationEntry(
            route = MeetDestinations.SERVICES_ACTIVE,
            label = "Servicios Activos",
            aliases = setOf("mis servicios", "trabajos activos", "pedidos en curso", "orden activa"),
            icon = "⚡"
        ),
        NavigationEntry(
            route = MeetDestinations.SERVICES_COMPLETED,
            label = "Historial",
            aliases = setOf("historial de servicios", "trabajos completados", "servicios completados", "historial forense"),
            icon = "📜"
        ),
        NavigationEntry(
            route = MeetDestinations.SAFETY_HOME,
            label = "Seguridad",
            aliases = setOf("seguridad y proteccion", "sos", "aura sentinel", "emergencia", "alertas"),
            icon = "🛡️"
        ),
        NavigationEntry(
            route = MeetDestinations.AGENT_STORE,
            label = "Tienda de Agentes",
            aliases = setOf("agentes", "tienda", "agent store", "compañeros", "draco", "personajes"),
            icon = "🤖"
        ),
        NavigationEntry(
            route = MeetDestinations.TRUST_CENTER,
            label = "Centro de Confianza",
            aliases = setOf("trust center", "verificación", "confianza", "peritaje oficial"),
            icon = "🏛️"
        ),
        NavigationEntry(
            route = MeetDestinations.SETTINGS,
            label = "Configuración",
            aliases = setOf("ajustes", "preferencias", "opciones", "perfil"),
            icon = "⚙️"
        ),
        NavigationEntry(
            route = MeetDestinations.DTCS,
            label = "Códigos DTC",
            aliases = setOf("fallas", "códigos", "codigos de falla", "averias"),
            icon = "⚠️"
        ),
        NavigationEntry(
            route = MeetDestinations.COMPONENT_LOCATOR,
            label = "Motor 3D",
            aliases = setOf("localizador 3d", "componentes", "motor", "sensores"),
            icon = "🧩"
        ),
        NavigationEntry(
            route = MeetDestinations.PROVIDER_SERVICES_CONFIG,
            label = "Configurar Mis Servicios",
            aliases = setOf("configurar tarifas", "mis tarifas", "catálogo de proveedor", "billetera de servicios"),
            icon = "💰"
        ),
    )

    fun findByQuery(query: String): NavigationEntry? {
        val normalized = UiTextNormalizer.normalize(query)
        if (normalized.isBlank()) return null
        val labels = entries.filter { UiTextNormalizer.normalize(it.label) == normalized }
        if (labels.isNotEmpty()) return labels.singleOrNull()
        val aliases = entries.filter { entry -> entry.aliases.any { UiTextNormalizer.normalize(it) == normalized } }
        if (aliases.isNotEmpty()) return aliases.singleOrNull()
        return entries.filter { entry ->
            (listOf(entry.label) + entry.aliases).any {
                UiTextNormalizer.normalize(it).contains(normalized)
            }
        }.singleOrNull()
    }
}
