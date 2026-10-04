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
    XENOLOGY,
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
    val commerce: AgentCommerceDescriptor? = null,
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
 * Cleaned of third-party IP (ASCENSION §5).
 * All characters, silhouettes, descriptions, names, voices, and assets belong to the original Elysium universe.
 */
object OfficialAgents {

    val EVAIR_CORE = AgentManifest(
        id = "agent.evair_core",
        displayName = "EVAIR Living Core",
        subtitle = "Compañero Autónomo & DigiSoul",
        description = "Espíritu cibernético guía para navegación, modo enseñanza y orquestación general de la plataforma con memoria viva y vínculo emocional.",
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
                name = "DigiSoul Core",
                capabilityId = "evair.digisoul_sync",
                domain = "DigiSoul",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Memoria episódica local y evolución DigiSoul según kilometraje y cuidado",
            ),
        ),
        isFree = true,
        requiredEntitlement = null,
        commerce = null,
        priceFiatCrc = 0L,
        badgeTag = "INCLUIDO",
        voiceSampleText = "¡Hola! Soy EVAIR. Tu espíritu compañero en cada kilómetro.",
        avatarVisualType = "EVAIR_SPIRIT",
        themeColorHex = 0xFF00E5FFL,
    )

    val MASTER_MECHANIC = AgentManifest(
        id = "agent.master_mechanic",
        displayName = "Vanguard Mecha",
        subtitle = "Master Mechanic & Forense OBD",
        description = "Mecha de diagnóstico de nivel de ingeniería: análisis profundo de DTCs, Mode $01, Mode $06, trim de combustible, guías de reparación y auditoría antifraude de cotizaciones de taller.",
        category = AgentCategory.AUTOMOTIVE,
        avatarAssetId = "avatar_master_mechanic",
        voiceProfileId = "voice_mechanic_pro",
        personalityProfileId = "personality_mechanic_rigorous",
        capabilityIds = listOf(
            "vehicle.read_dtc",
            "vehicle.mode06",
            "vehicle.fuel_trim",
            "quote.audit_fraud",
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
                name = "Auditoría Antifraude de Taller",
                capabilityId = "quote.audit_fraud",
                domain = "Finance",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Tablas de mano de obra OEM y regla estricta de compatibilidad de repuestos",
            ),
        ),
        isFree = false,
        requiredEntitlement = "agent.master_mechanic",
        commerce = AgentCommerceDescriptor(
            storeProductId = "agent_master_mechanic_lifetime",
            entitlementId = "agent.master_mechanic",
        ),
        priceFiatCrc = 2990L,
        originalPriceFiatCrc = 4500L,
        badgeTag = "PREMIUM",
        voiceSampleText = "Sistemas Mecha online. Escaneo de sensores completado sin errores.",
        avatarVisualType = "CYBER_MECHA",
        themeColorHex = 0xFFFF9100L,
    )

    val DRACO_DRAGON = AgentManifest(
        id = "agent.draco_dragon",
        displayName = "Draco Ignis",
        subtitle = "Dragón Cuántico & Potencia Térmica",
        description = "Dragón cibernético ancestral: guardián de la combustión, empuje cinemático y refrigeración de motor con visión termodinámica y aliento de datos.",
        category = AgentCategory.AUTOMOTIVE,
        avatarAssetId = "avatar_draco_dragon",
        voiceProfileId = "voice_dragon_deep",
        personalityProfileId = "personality_dragon_fierce",
        capabilityIds = listOf(
            "vehicle.power_boost",
            "vehicle.thermal_monitor",
            "system.voice_assistant",
        ),
        detailedCapabilities = listOf(
            CapabilityPackDetail(
                name = "Aliento Térmico de Diagnóstico",
                capabilityId = "vehicle.thermal_monitor",
                domain = "Diagnostic",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Supervisión de curva de temperatura de refrigerante y aceite",
            ),
            CapabilityPackDetail(
                name = "Empuje de Potencia Cinemática",
                capabilityId = "vehicle.power_boost",
                domain = "Performance",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Análisis dinámico de par motor y flujo volumétrico de admisión",
            ),
        ),
        isFree = false,
        requiredEntitlement = "agent.draco_dragon",
        commerce = AgentCommerceDescriptor(
            storeProductId = "agent_draco_dragon_lifetime",
            entitlementId = "agent.draco_dragon",
        ),
        priceFiatCrc = 1990L,
        originalPriceFiatCrc = 3000L,
        badgeTag = "DESTACADO",
        voiceSampleText = "¡La fuerza de tu motor ruge bajo mis alas! Listos para devorar el asfalto.",
        avatarVisualType = "DRAGON",
        themeColorHex = 0xFFFF3B30L,
    )

    val VOLT_AETHER = AgentManifest(
        id = "agent.volt_aether",
        displayName = "Volt Aether",
        subtitle = "Compañero Eléctrico & Baterías",
        description = "Espíritu voltaico de alta frecuencia: cuida la salud de la batería, alternador, bobinas de encendido y chispa de bujías con precisión electrodinámica.",
        category = AgentCategory.CORE,
        avatarAssetId = "avatar_volt_aether",
        voiceProfileId = "voice_volt_chirp",
        personalityProfileId = "personality_volt_playful",
        capabilityIds = listOf(
            "battery.state_of_health",
            "ignition.spark_monitor",
            "system.voice_assistant",
        ),
        detailedCapabilities = listOf(
            CapabilityPackDetail(
                name = "Chispa Eléctrica Volt",
                capabilityId = "battery.state_of_health",
                domain = "Electrical",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Monitoreo continuo de CCA de batería y ondulación del alternador",
            ),
            CapabilityPackDetail(
                name = "Descarga de Apoyo",
                capabilityId = "ignition.spark_monitor",
                domain = "Ignition",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Detección de pérdidas de chispa por cilindro en tiempo real",
            ),
        ),
        isFree = false,
        requiredEntitlement = "agent.volt_aether",
        commerce = AgentCommerceDescriptor(
            storeProductId = "agent_volt_aether_lifetime",
            entitlementId = "agent.volt_aether",
        ),
        priceFiatCrc = 1490L,
        originalPriceFiatCrc = 2500L,
        badgeTag = "ENERGÍA",
        voiceSampleText = "¡Volt Aether en línea! Chispas listas y batería al cien por ciento.",
        avatarVisualType = "VOLT_AETHER",
        themeColorHex = 0xFFFFD700L,
    )

    val TITAN_VANGUARD = AgentManifest(
        id = "agent.titan_vanguard",
        displayName = "Titan Vanguard",
        subtitle = "Escudo Primordial & Telemetría Extrema",
        description = "Guardián cinemático de fuerza primordial: telemetría de impacto extrema, control dinámico de tracción en pendientes y protección SOS en carretera.",
        category = AgentCategory.SAFETY,
        avatarAssetId = "avatar_titan_vanguard",
        voiceProfileId = "voice_titan_primal",
        personalityProfileId = "personality_titan_warrior",
        capabilityIds = listOf(
            "safety.collision_telemetry",
            "traction.ki_burst",
            "sos.authoritative_dispatch",
        ),
        detailedCapabilities = listOf(
            CapabilityPackDetail(
                name = "Aura Protectora de Impacto",
                capabilityId = "safety.collision_telemetry",
                domain = "Safety",
                riskLevel = "SAFETY_CRITICAL",
                physicalModelDescription = "Escudo cinemático de desaceleración y telemetría inercial extrema",
            ),
            CapabilityPackDetail(
                name = "Despacho SOS Vanguard",
                capabilityId = "sos.authoritative_dispatch",
                domain = "Emergency",
                riskLevel = "SAFETY_CRITICAL",
                physicalModelDescription = "Rutas de rescate prioritarias y enlace directo con auxilio 24/7",
            ),
        ),
        isFree = false,
        requiredEntitlement = "agent.titan_vanguard",
        commerce = AgentCommerceDescriptor(
            storeProductId = "agent_titan_vanguard_lifetime",
            entitlementId = "agent.titan_vanguard",
        ),
        priceFiatCrc = 1990L,
        originalPriceFiatCrc = 3500L,
        badgeTag = "SEGURIDAD",
        voiceSampleText = "¡Titan Vanguard al mando! Escudo inercial activo. Ningún obstáculo detendrá nuestra marcha.",
        avatarVisualType = "TITAN_VANGUARD",
        themeColorHex = 0xFFFF1744L,
    )

    val LAYA_VALKYRIE = AgentManifest(
        id = "agent.laya_valkyrie",
        displayName = "Laya Celestial",
        subtitle = "Valquiria Cuántica & Rutas",
        description = "Inteligencia celestial ejecutiva: geocodificación espacial en Costa Rica, rutas de máximo confort y asistencia cuántica en tiempo real.",
        category = AgentCategory.MOBILITY,
        avatarAssetId = "avatar_laya_valkyrie",
        voiceProfileId = "voice_laya_celestial",
        personalityProfileId = "personality_laya_serene",
        capabilityIds = listOf(
            "ride.request",
            "mobility.dispatch_optimize",
            "system.voice_assistant",
        ),
        detailedCapabilities = listOf(
            CapabilityPackDetail(
                name = "Navegación Cuántica Laya",
                capabilityId = "mobility.dispatch_optimize",
                domain = "Navigation",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Geocodificación espacial local de Costa Rica con cero latencia",
            ),
        ),
        isFree = true,
        requiredEntitlement = null,
        commerce = null,
        priceFiatCrc = 0L,
        badgeTag = "INCLUIDO",
        voiceSampleText = "Soy Laya Celestial. El camino está despejado. Guiando tus pasos con precisión absoluta.",
        avatarVisualType = "LAYA_VALKYRIE",
        themeColorHex = 0xFFBB00FFL,
    )

    // ── Alien Race Agents — Xenología Elysium ──────────────────────────

    val REPTILIAN = AgentManifest(
        id = "agent.reptilian",
        displayName = "Reptiliano",
        subtitle = "Inteligencia Dracónica & Programas Ocultos",
        description = "Entidad reptiliana asociada a programas militares secretos. Nave asignada: Tic-Tac UAP. Aspecto escamoso, ojos con pupila vertical, presencia intimidante.",
        category = AgentCategory.XENOLOGY,
        avatarAssetId = "avatar_reptilian",
        voiceProfileId = "voice_reptilian_deep",
        personalityProfileId = "personality_reptilian_cold",
        capabilityIds = listOf(
            "xeno.race_info",
            "xeno.uap_catalog",
        ),
        detailedCapabilities = listOf(
            CapabilityPackDetail(
                name = "Inteligencia Dracónica",
                capabilityId = "xeno.race_info",
                domain = "Xenology",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Base de datos xenológica: razas, avistamientos, tipología de naves",
            ),
        ),
        isFree = true,
        requiredEntitlement = null,
        commerce = null,
        priceFiatCrc = 0L,
        badgeTag = "XENOLOGÍA",
        voiceSampleText = "Somos los que observan desde las sombras. Nuestras naves Tic-Tac surcan sus cielos sin ser detectadas.",
        avatarVisualType = "REPTILIAN",
        themeColorHex = 0xFF4CAF50L,
    )

    val NORDIC = AgentManifest(
        id = "agent.nordic",
        displayName = "Nórdico",
        subtitle = "Inteligencia Benevolente & Guía Estelar",
        description = "Entidad nórdica de apariencia humanoide, alta, cabello platino. Asociada a intención benevolente. Nave asignada: Crescent/Wedge UAP.",
        category = AgentCategory.XENOLOGY,
        avatarAssetId = "avatar_nordic",
        voiceProfileId = "voice_nordic_serene",
        personalityProfileId = "personality_nordic_wise",
        capabilityIds = listOf(
            "xeno.race_info",
            "xeno.uap_catalog",
        ),
        detailedCapabilities = listOf(
            CapabilityPackDetail(
                name = "Guía Estelar Nórdica",
                capabilityId = "xeno.race_info",
                domain = "Xenology",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Conocimiento ancestral de razas estelares y rutas interdimensionales",
            ),
        ),
        isFree = true,
        requiredEntitlement = null,
        commerce = null,
        priceFiatCrc = 0L,
        badgeTag = "XENOLOGÍA",
        voiceSampleText = "Venimos en paz. Nuestra nave Crescent surca las dimensiones para guiar la evolución consciente.",
        avatarVisualType = "NORDIC",
        themeColorHex = 0xFF42A5F5L,
    )

    val GREY = AgentManifest(
        id = "agent.grey",
        displayName = "Gris",
        subtitle = "Observador Silencioso & Experimentación",
        description = "Entidad gris de apariencia delgada, ojos negros almendrados enormes. Asociada a abducción y experimentación. Nave asignada: Lenticular UAP.",
        category = AgentCategory.XENOLOGY,
        avatarAssetId = "avatar_grey",
        voiceProfileId = "voice_grey_telepathic",
        personalityProfileId = "personality_grey_analytical",
        capabilityIds = listOf(
            "xeno.race_info",
            "xeno.uap_catalog",
        ),
        detailedCapabilities = listOf(
            CapabilityPackDetail(
                name = "Análisis Xenobiológico",
                capabilityId = "xeno.race_info",
                domain = "Xenology",
                riskLevel = "READ_ONLY",
                physicalModelDescription = "Catalogación de especímenes y análisis de frecuencias de contacto",
            ),
        ),
        isFree = true,
        requiredEntitlement = null,
        commerce = null,
        priceFiatCrc = 0L,
        badgeTag = "XENOLOGÍA",
        voiceSampleText = "No necesitamos palabras. Nuestra nave Lenticular ya está aquí. Solo observamos.",
        avatarVisualType = "GREY",
        themeColorHex = 0xFF78909CL,
    )

    // Backward-compatible architectural aliases
    val VANGUARD_SENTINEL = TITAN_VANGUARD
    val MOBILITY_PRIME = VOLT_AETHER
    val EMISSIONS_SPECIALIST = DRACO_DRAGON

    val ALL = listOf(
        EVAIR_CORE,
        MASTER_MECHANIC,
        DRACO_DRAGON,
        VOLT_AETHER,
        TITAN_VANGUARD,
        LAYA_VALKYRIE,
        REPTILIAN,
        NORDIC,
        GREY,
    )
}
