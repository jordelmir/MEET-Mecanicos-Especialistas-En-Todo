package com.elysium369.meet.ui.screens.home.adaptive

import com.elysium369.meet.ui.navigation.MeetDestinations

object HomeModuleRegistry {

    fun getModulesByCategory(
        userRole: String,
        isPlatformOwner: Boolean = false
    ): Map<HomeSectionCategory, List<HomeModuleItem>> {
        val map = mutableMapOf<HomeSectionCategory, MutableList<HomeModuleItem>>()

        HomeSectionCategory.entries.forEach { section ->
            map[section] = mutableListOf()
        }

        // ── DIAGNOSTICS & CONTROL ──
        map[HomeSectionCategory.DIAGNOSTICS]?.addAll(
            listOf(
                HomeModuleItem("scanner", "Escáner en Vivo", "Telemetría de sensores y PIDs", MeetDestinations.SCANNER, HomeSectionCategory.DIAGNOSTICS, "scanner", isHighlight = true),
                HomeModuleItem("dtcs", "Códigos DTC", "Lectura y borrado de fallas ECU", MeetDestinations.DTCS, HomeSectionCategory.DIAGNOSTICS, "dtc", isHighlight = true),
                HomeModuleItem("perito", "Vanguard Perito", "Inspección forense pre-compra", MeetDestinations.PERITO, HomeSectionCategory.DIAGNOSTICS, "vanguard_perito"),
                HomeModuleItem("dna", "Vanguard DNA", "Perfil biométrico y gemelo digital", MeetDestinations.DNA, HomeSectionCategory.DIAGNOSTICS, "vanguard_dna"),
                HomeModuleItem("access_immo", "Acceso & IMMO", "Llaves digitales, transponders y BCM", MeetDestinations.VEHICLE_ACCESS, HomeSectionCategory.DIAGNOSTICS, "vehicle_access", badgeText = "NUEVO"),
                HomeModuleItem("findings", "Hallazgos", "Evidencias y registros técnicos", MeetDestinations.FINDINGS, HomeSectionCategory.DIAGNOSTICS, "findings"),
                HomeModuleItem("terminal", "Terminal OBD", "Comandos crudos ELM/AT y ST", MeetDestinations.TERMINAL, HomeSectionCategory.DIAGNOSTICS, "terminal")
            )
        )

        // ── VEHICLE & HISTORY ──
        map[HomeSectionCategory.VEHICLE]?.addAll(
            listOf(
                HomeModuleItem("garage", "Garaje Digital", "Vehículos registrados y perfiles", MeetDestinations.GARAGE, HomeSectionCategory.VEHICLE, "garage", isHighlight = true),
                HomeModuleItem("health", "Score de Salud", "Algoritmo integral de condición", MeetDestinations.HEALTH_SCORE, HomeSectionCategory.VEHICLE, "health_score"),
                HomeModuleItem("maintenance", "Mantenimiento", "Alertas de fluidos, filtros y servicios", MeetDestinations.MAINTENANCE, HomeSectionCategory.VEHICLE, "maintenance"),
                HomeModuleItem("trips", "Bitácora de Viajes", "Consumo, distancias y telemetría", MeetDestinations.TRIP_LOG, HomeSectionCategory.VEHICLE, "trip_log"),
                HomeModuleItem("dvir", "Inspección DVIR", "Reporte de inspección diaria", MeetDestinations.DVIR, HomeSectionCategory.VEHICLE, "dvir"),
                HomeModuleItem("battery", "Salud de Batería", "Diagnóstico de acumulador y carga", MeetDestinations.BATTERY_HEALTH, HomeSectionCategory.VEHICLE, "battery_health")
            )
        )

        // ── SERVICES & ASSISTANCE ──
        map[HomeSectionCategory.SERVICES]?.addAll(
            listOf(
                HomeModuleItem("learning", "MEET Aprende", "Capacidades, misiones y teórico de manejo", MeetDestinations.LEARNING_HUB, HomeSectionCategory.SERVICES, "learning_hub", isHighlight = true, badgeText = "NUEVO"),
                HomeModuleItem("messages", "Mensajes", "Chats y llamadas privadas Elysium", MeetDestinations.MESSAGES, HomeSectionCategory.SERVICES, "messages", isHighlight = true),
                HomeModuleItem("mechanic", "Servicios Mecánicos", "Red de talleres y cotizaciones", MeetDestinations.MECHANIC_SERVICES, HomeSectionCategory.SERVICES, "mechanic_services", isHighlight = true),
                HomeModuleItem("parts", "Repuestos & Piezas", "Compatibilidad técnica VIN-DTC", MeetDestinations.PARTS_STORE, HomeSectionCategory.SERVICES, "parts_store"),
                HomeModuleItem("tow_truck", "Asistencia & Grúa", "Auxilio vial geolocalizado", MeetDestinations.TOW_TRUCK, HomeSectionCategory.SERVICES, "tow_truck"),
                HomeModuleItem("live_link", "Live Link PRO", "Sesión remota perito/mecánico", MeetDestinations.LIVE_STREAM, HomeSectionCategory.SERVICES, "live_stream"),
                HomeModuleItem("trust_center", "Trust Center", "Garantías y pagos protegidos", MeetDestinations.TRUST_CENTER, HomeSectionCategory.SERVICES, "trust_center"),
                HomeModuleItem("ride", "MEET Rides", "Transporte inteligente y conductor", MeetDestinations.RIDE_HOME, HomeSectionCategory.SERVICES, "ride_home")
            )
        )

        // ── ADVANCED TOOLS ──
        map[HomeSectionCategory.TOOLS]?.addAll(
            listOf(
                HomeModuleItem("ai_copilot", "IA Especialista", "Diagnóstico guiado por inteligencia", MeetDestinations.AI, HomeSectionCategory.TOOLS, "ai", isHighlight = true),
                HomeModuleItem("engine_3d", "Motor 3D Interactivo", "Visor tridimensional de componentes", MeetDestinations.ENGINE_3D, HomeSectionCategory.TOOLS, "engine_3d"),
                HomeModuleItem("hud", "Modo HUD", "Proyección en parabrisas", MeetDestinations.HUD, HomeSectionCategory.TOOLS, "hud"),
                HomeModuleItem("protocol", "Protocolos", "Detección y parámetros de bus", MeetDestinations.PROTOCOL_LEARNING, HomeSectionCategory.TOOLS, "protocol_learning"),
                HomeModuleItem("adapter", "Test Adaptador", "Rendimiento y clones ELM327", MeetDestinations.ADAPTER_DIAGNOSTICS, HomeSectionCategory.TOOLS, "adapter_diagnostics")
            )
        )

        // ── PROFESSIONAL & FLEET ──
        val proList = mutableListOf(
            HomeModuleItem("pro_hub", "Vanguard PRO Hub", "Topología CAN y test de actuadores", MeetDestinations.PRO_HUB, HomeSectionCategory.PROFESSIONAL, "pro_hub", isHighlight = true),
            HomeModuleItem("fleet", "Gestión de Flota", "Supervisión y despacho multi-unidad", "fleet", HomeSectionCategory.PROFESSIONAL, "fleet")
        )

        if (isPlatformOwner) {
            proList.addAll(
                listOf(
                    HomeModuleItem("owner_cockpit", "Owner Control", "Cockpit global de plataforma", "platform_owner_cockpit", HomeSectionCategory.PROFESSIONAL, "platform_owner_cockpit"),
                    HomeModuleItem("system_health", "System Health", "Estado del clúster y nodos", "system_health_dashboard", HomeSectionCategory.PROFESSIONAL, "system_health_dashboard")
                )
            )
        }
        map[HomeSectionCategory.PROFESSIONAL]?.addAll(proList)

        return map.filterValues { it.isNotEmpty() }
    }
}
