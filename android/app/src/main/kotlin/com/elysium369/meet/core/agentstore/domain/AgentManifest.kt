package com.elysium369.meet.core.agentstore.domain

import kotlinx.serialization.Serializable

enum class AgentCategory {
    CORE,
    AUTOMOTIVE,
    SAFETY,
    MOBILITY,
    EMISSIONS,
    BUSINESS,
    FLEET,
}

enum class AgentReleaseState {
    OFFICIAL,
    BETA,
    PREVIEW,
    DEPRECATED,
}

@Serializable
data class CapabilityPackDetail(
    val name: String,
    val capabilityId: String,
    val domain: String,
    val riskLevel: String,
    val physicalModelDescription: String,
)

@Serializable
data class AgentManifest(
    val id: String,
    val version: String = "1.0.0",
    val displayName: String,
    val subtitle: String,
    val description: String,
    val category: AgentCategory,
    val avatarAssetId: String,
    val voiceProfileId: String,
    val personalityProfileId: String,
    val capabilityIds: List<String>,
    val detailedCapabilities: List<CapabilityPackDetail> = emptyList(),
    val knowledgePackIds: List<String> = emptyList(),
    val minimumAppVersion: String = "4.26.0",
    val requiredEntitlement: String? = null,
    val isFree: Boolean = (requiredEntitlement == null),
    val priceFiatCrc: Long = 0L,
    val originalPriceFiatCrc: Long? = null,
    val badgeTag: String? = null,
    val voiceSampleText: String = "",
    val avatarVisualType: String = "QUANTUM_SPHERE",
    val themeColorHex: Long = 0xFF00E5FFL,
    val safetyPolicyVersion: String = "2026.1",
    val privacyDescription: String = "Opera exclusivamente con datos locales o autorizados explícitamente.",
    val supportedLanguages: List<String> = listOf("es", "en"),
    val releaseState: AgentReleaseState = AgentReleaseState.OFFICIAL,
)

/**
 * Standard roster of official Elysium collectible agents with real capabilities.
 * Master Order Omega §30, §31, §32, §33.
 */
object OfficialAgents {

    val EVAIR_CORE = AgentManifest(
        id = "agent.evair_core",
        displayName = "EVAIR Core",
        subtitle = "Compañero Autónomo & DigiSoul",
        description = "Asistente universal inteligente para navegación, modo enseñanza y orquestación general de la plataforma con memoria viva y vínculo emocional.",
        category = AgentCategory.CORE,
        avatarAssetId = "avatar_evair_living_core",
        voiceProfileId = "voice_evair_synth_v1",
        personalityProfileId = "personality_evair_helpful",
        capabilityIds = listOf(
            "guide.start_tutorial",
            "ride.request",
            "evair.digisoul_sync",
            "system.voice_assistant",
        ),
        detailedCapabilities = listOf(
            CapabilityPackDetail(
                name = "Tutor de Conducción",
                capabilityId = "guide.start_tutorial",
                domain = "Guide",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Tutorial heurístico no intrusivo con detección de contexto de usuario",
            ),
            CapabilityPackDetail(
                name = "Solicitud de Movilidad",
                capabilityId = "ride.request",
                domain = "Ride",
                riskLevel = "COMMITTING",
                physicalModelDescription = "Tarificación canónica CR GAM con contrato de paridad inmutable",
            ),
            CapabilityPackDetail(
                name = "DigiSoul Core",
                capabilityId = "evair.digisoul_sync",
                domain = "DigiSoul",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Memoria episódica local y evolución DigiSoul según kilometraje y cuidado",
            ),
            CapabilityPackDetail(
                name = "Voz Conversacional Laya",
                capabilityId = "system.voice_assistant",
                domain = "System",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Procesamiento bidireccional no auto-regresivo en <33ms sin nube",
            ),
        ),
        isFree = true,
        requiredEntitlement = null,
        priceFiatCrc = 0L,
        badgeTag = "INCLUIDO",
        voiceSampleText = "¡Hola! Soy EVAIR. Estoy en línea y listo para acompañarte en cada kilómetro del viaje.",
        avatarVisualType = "QUANTUM_SPHERE",
        themeColorHex = 0xFF00E5FFL,
    )

    val MASTER_MECHANIC = AgentManifest(
        id = "agent.master_mechanic",
        displayName = "Vanguard Titan",
        subtitle = "Master Mechanic & Forense OBD",
        description = "Inteligencia automotriz avanzada para análisis profundo de DTCs, Mode $01, Mode $06, trim de combustible, guías de reparación y auditoría antifraude de cotizaciones de taller.",
        category = AgentCategory.AUTOMOTIVE,
        avatarAssetId = "avatar_master_mechanic",
        voiceProfileId = "voice_mechanic_pro",
        personalityProfileId = "personality_mechanic_rigorous",
        capabilityIds = listOf(
            "vehicle.read_dtc",
            "vehicle.mode06",
            "vehicle.fuel_trim",
            "vehicle.freeze_frame",
            "quote.audit_fraud",
            "ecu.protocol_negotiate",
        ),
        detailedCapabilities = listOf(
            CapabilityPackDetail(
                name = "Diagnóstico DTC Profundo",
                capabilityId = "vehicle.read_dtc",
                domain = "Diagnostic",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Correlación física de falla con árboles de decisión SAE y causa raíz",
            ),
            CapabilityPackDetail(
                name = "Monitoreo Mode $06",
                capabilityId = "vehicle.mode06",
                domain = "Diagnostic",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Límites de prueba de componentes fuera de rango en monitores continuos",
            ),
            CapabilityPackDetail(
                name = "Auditoría Antifraude de Taller",
                capabilityId = "quote.audit_fraud",
                domain = "Finance",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Tablas de mano de obra OEM y regla estricta de compatibilidad de repuestos",
            ),
            CapabilityPackDetail(
                name = "Negociador de Protocolo ECU",
                capabilityId = "ecu.protocol_negotiate",
                domain = "ECU",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Alineación de cabeceras CAN 11/29-bit con protección contra clones",
            ),
        ),
        isFree = false,
        requiredEntitlement = "agent.master_mechanic",
        priceFiatCrc = 2_990L,
        originalPriceFiatCrc = 4_500L,
        badgeTag = "OFERTA -33%",
        voiceSampleText = "Soy Vanguard Titan. Diagnóstico mecatrónico inicializado. Cero conjeturas: solo evidencia física comprobable.",
        avatarVisualType = "TITAN_EXOSKELETON",
        themeColorHex = 0xFFFFB300L,
    )

    val VANGUARD_SENTINEL = AgentManifest(
        id = "agent.vanguard_sentinel",
        displayName = "Aura Sentinel",
        subtitle = "Guardián Táctico & Seguridad de Flota",
        description = "Escudo de seguridad en tiempo real: monitoreo de fuerzas G cinemáticas (>3.8G), detección de desvíos de ruta, geocercas activas y botón de despacho SOS autoritativo.",
        category = AgentCategory.SAFETY,
        avatarAssetId = "avatar_vanguard_sentinel",
        voiceProfileId = "voice_sentinel_tactical",
        personalityProfileId = "personality_sentinel_vigilant",
        capabilityIds = listOf(
            "safety.collision_telemetry",
            "ride.anomaly_filter",
            "security.antitheft_fence",
            "sos.authoritative_dispatch",
        ),
        detailedCapabilities = listOf(
            CapabilityPackDetail(
                name = "Telemetría de Impacto",
                capabilityId = "safety.collision_telemetry",
                domain = "Safety",
                riskLevel = "SAFETY_CRITICAL",
                physicalModelDescription = "Acelerometría inercial >3.8G y desbalance cinemático de colisión",
            ),
            CapabilityPackDetail(
                name = "Filtro de Desvíos de Ruta",
                capabilityId = "ride.anomaly_filter",
                domain = "Mobility",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Detección de paradas no programadas y desviación angular de ruta",
            ),
            CapabilityPackDetail(
                name = "Geocercas de Protección",
                capabilityId = "security.antitheft_fence",
                domain = "Security",
                riskLevel = "COMMITTING",
                physicalModelDescription = "Alerta inmediata de cruce de perímetro no autorizado",
            ),
            CapabilityPackDetail(
                name = "Despacho SOS Autoritativo",
                capabilityId = "sos.authoritative_dispatch",
                domain = "Safety",
                riskLevel = "SAFETY_CRITICAL",
                physicalModelDescription = "Cero intermediarios sintéticos hacia el centro de respuesta de emergencia",
            ),
        ),
        isFree = false,
        requiredEntitlement = "agent.vanguard_sentinel",
        priceFiatCrc = 1_990L,
        originalPriceFiatCrc = 3_500L,
        badgeTag = "SEGURIDAD 24/7",
        voiceSampleText = "Aura Sentinel activo. Escudo de telemetría desplegado. Vigilando cada vector del vehículo.",
        avatarVisualType = "TACTICAL_SHIELD",
        themeColorHex = 0xFFFF3B30L,
    )

    val MOBILITY_PRIME = AgentManifest(
        id = "agent.mobility_prime",
        displayName = "Neo Concierge",
        subtitle = "Movilidad Ejecutiva & Eficiencia",
        description = "Inteligencia ejecutiva de transporte: optimización de rutas con inercia de combustible, despacho VIP de movilidad y proyecciones de flujo de caja para conductores y flotas.",
        category = AgentCategory.MOBILITY,
        avatarAssetId = "avatar_mobility_prime",
        voiceProfileId = "voice_concierge_smooth",
        personalityProfileId = "personality_concierge_vip",
        capabilityIds = listOf(
            "ride.request",
            "mobility.dispatch_optimize",
            "fuel.efficiency_route",
            "fleet.cashflow_forecast",
        ),
        detailedCapabilities = listOf(
            CapabilityPackDetail(
                name = "Despacho VIP",
                capabilityId = "mobility.dispatch_optimize",
                domain = "Mobility",
                riskLevel = "COMMITTING",
                physicalModelDescription = "Algoritmo de asignación de choferes con menor fricción y tiempo de espera",
            ),
            CapabilityPackDetail(
                name = "Rutas de Ahorro de Combustible",
                capabilityId = "fuel.efficiency_route",
                domain = "Navigation",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Aprovechamiento de pendiente e inercia térmica para reducir consumo",
            ),
            CapabilityPackDetail(
                name = "Proyección de Flujo de Caja",
                capabilityId = "fleet.cashflow_forecast",
                domain = "Finance",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Estimación de ingresos netos post-comisión canónica de plataforma",
            ),
        ),
        isFree = false,
        requiredEntitlement = "agent.mobility_prime",
        priceFiatCrc = 1_490L,
        originalPriceFiatCrc = 2_500L,
        badgeTag = "POPULAR",
        voiceSampleText = "Bienvenido a bordo. Soy Neo Concierge. He calculado la ruta más eficiente y confortable para tu destino.",
        avatarVisualType = "AERODYNAMIC_CONCIERGE",
        themeColorHex = 0xFF00E676L,
    )

    val EMISSIONS_SPECIALIST = AgentManifest(
        id = "agent.emissions_specialist",
        displayName = "Dekra Metrologist",
        subtitle = "Pre-ITV & Metrología de Emisiones",
        description = "Especialista técnico en inspección vehicular Pre-ITV / DEKRA, oscilograma de sensor O₂, eficiencia de convertidor catalítico y metrología con trazabilidad de procedencia.",
        category = AgentCategory.EMISSIONS,
        avatarAssetId = "avatar_emissions_specialist",
        voiceProfileId = "voice_emissions_lab",
        personalityProfileId = "personality_emissions_forensic",
        capabilityIds = listOf(
            "emissions.analyze_o2",
            "emissions.catalyst_analysis",
            "emissions.pre_itv",
            "emissions.combustion_risk",
        ),
        detailedCapabilities = listOf(
            CapabilityPackDetail(
                name = "Oscilograma O₂ Burst",
                capabilityId = "emissions.analyze_o2",
                domain = "Emissions",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Frecuencia de conmutación 0.1V - 0.9V en circuito cerrado",
            ),
            CapabilityPackDetail(
                name = "Auditoría Dekra Pre-ITV",
                capabilityId = "emissions.pre_itv",
                domain = "Emissions",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Reglamento costarricense con lambda 0.970 - 1.030 y humo K <= 2.5 m^-1",
            ),
            CapabilityPackDetail(
                name = "Eficiencia Catalítica",
                capabilityId = "emissions.catalyst_analysis",
                domain = "Emissions",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Retardo de fase sensor 1 vs sensor 2 y capacidad de almacenamiento de O₂",
            ),
        ),
        isFree = false,
        requiredEntitlement = "agent.emissions_specialist",
        priceFiatCrc = 1_990L,
        originalPriceFiatCrc = 3_000L,
        badgeTag = "PRE-ITV",
        voiceSampleText = "Dekra Metrologist listo. Verificando relación estequiométrica Lambda y umbrales de emisiones para ITV.",
        avatarVisualType = "METROLOGY_PRISM",
        themeColorHex = 0xFFBB00FFL,
    )

    val ALL = listOf(
        EVAIR_CORE,
        MASTER_MECHANIC,
        VANGUARD_SENTINEL,
        MOBILITY_PRIME,
        EMISSIONS_SPECIALIST,
    )
}
