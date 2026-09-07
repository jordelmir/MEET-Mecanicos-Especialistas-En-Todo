package com.elysium369.meet.ui.navigation

/**
 * MEET Product Topology — Reorganizes the entire platform around 5 primary universes
 * with Elysium AI operating as a transversal intelligence layer across all capabilities.
 */
enum class ProductUniverse(
    val title: String,
    val subtitle: String,
    val icon: String
) {
    MY_VEHICLE(
        title = "Mi Vehículo",
        subtitle = "Salud, Diagnóstico, Mantenimiento, Historial y Passport",
        icon = "🚗"
    ),
    RESOLVE(
        title = "Resolver",
        subtitle = "Reparación, Red de Talleres, Partes, Grúas y Live Expert",
        icon = "🔧"
    ),
    DRIVE(
        title = "Conducir",
        subtitle = "Live Data, Gauges, HUD, Dashcam y Viajes Eco",
        icon = "⚡"
    ),
    MOBILITY(
        title = "Movilidad",
        subtitle = "Rides, Ofertas, Despacho, PIN Seguro y Seguimiento",
        icon = "📍"
    ),
    PROFESSIONAL(
        title = "Profesional",
        subtitle = "Pro Hub, Workshop OS, Fleet OS y Herramientas Expertas",
        icon = "🛡️"
    ),
    ELYSIUM_AI(
        title = "Elysium AI",
        subtitle = "Capa Transversal de Inteligencia Automotriz y Copiloto Mecánico",
        icon = "✨"
    );

    companion object {
        fun resolveUniverse(destination: String): ProductUniverse {
            return when (destination.substringBefore("?").substringBefore("/")) {
                MeetDestinations.GARAGE,
                MeetDestinations.HEALTH_SCORE,
                MeetDestinations.DNA,
                MeetDestinations.CAPABILITY_PASSPORT,
                MeetDestinations.VEHICLE_ACCESS,
                MeetDestinations.MAINTENANCE,
                "vehicle_detail" -> MY_VEHICLE

                MeetDestinations.MECHANIC_SERVICES,
                MeetDestinations.TOW_TRUCK,
                MeetDestinations.PARTS_STORE,
                "part_request",
                "dekra_concierge",
                "repair" -> RESOLVE

                MeetDestinations.HUD,
                MeetDestinations.TRIP_LOG,
                "dashcam",
                "gauges",
                "eco_trips",
                "camera_hud" -> DRIVE

                MeetDestinations.RIDE_HOME,
                MeetDestinations.RIDE_DRIVER_MODE,
                MeetDestinations.MESSAGES,
                "rides" -> MOBILITY

                MeetDestinations.PRO_HUB,
                MeetDestinations.LEARNING_HUB,
                MeetDestinations.ELYSIUM_LEARNING_OS,
                MeetDestinations.TERMINAL,
                MeetDestinations.DVIR,
                MeetDestinations.MULTIMETER_SIMULATION,
                MeetDestinations.ADAPTER_DIAGNOSTICS,
                MeetDestinations.PROTOCOL_LEARNING,
                "oscilloscope",
                "active_tests",
                "service_resets" -> PROFESSIONAL

                MeetDestinations.AI,
                "evair",
                "elysium_ai" -> ELYSIUM_AI

                else -> MY_VEHICLE
            }
        }
    }
}
