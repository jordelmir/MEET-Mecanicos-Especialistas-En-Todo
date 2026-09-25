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

    val DRACO_DRAGON = AgentManifest(
        id = "agent.draco_dragon",
        displayName = "Draco Ignis",
        subtitle = "Dragón Místico & Potencia de Motor",
        description = "Dragón cibernético ancestral: guardián de la combustión, empuje cinemático y refrigeración de motor con visión térmica y aliento de datos.",
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
        isFree = true,
        requiredEntitlement = null,
        priceFiatCrc = 0L,
        badgeTag = "DESBLOQUEADO",
        voiceSampleText = "¡Roaaar! Soy Draco Ignis. La fuerza de tu motor ruge bajo mis alas. ¡Listos para devorar el asfalto!",
        avatarVisualType = "DRAGON",
        themeColorHex = 0xFFFF3B30L,
    )

    val POKEMON_VOLT = AgentManifest(
        id = "agent.pokemon_volt",
        displayName = "Volt Sparky",
        subtitle = "Compañero Eléctrico & Baterías",
        description = "Criatura eléctrica hiperactiva estilo Pokémon: cuida la salud de la batería, alternador, bobinas de encendido y chispa de bujías con ternura y poder voltaico.",
        category = AgentCategory.CORE,
        avatarAssetId = "avatar_pokemon_volt",
        voiceProfileId = "voice_sparky_chirp",
        personalityProfileId = "personality_sparky_playful",
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
        isFree = true,
        requiredEntitlement = null,
        priceFiatCrc = 0L,
        badgeTag = "DESBLOQUEADO",
        voiceSampleText = "¡Pika-volt! ¡Chispas listas y batería al cien! ¡Vamos a rodar felices por Costa Rica!",
        avatarVisualType = "POKEMON_VOLT",
        themeColorHex = 0xFFFFD700L,
    )

    val SAIYAN_SSJ4 = AgentManifest(
        id = "agent.saiyan_ssj4",
        displayName = "Titan Saiyan SSJ4",
        subtitle = "Ki Primordial & Telemetría Extrema",
        description = "Guerrero legendario del Ki Carmesí: fuerza descomunal para rescate en carretera, telemetría de impacto extrema, tracción en pendientes y protección inquebrantable.",
        category = AgentCategory.SAFETY,
        avatarAssetId = "avatar_saiyan_ssj4",
        voiceProfileId = "voice_saiyan_primal",
        personalityProfileId = "personality_saiyan_warrior",
        capabilityIds = listOf(
            "safety.collision_telemetry",
            "traction.ki_burst",
            "sos.authoritative_dispatch",
        ),
        detailedCapabilities = listOf(
            CapabilityPackDetail(
                name = "Aura de Ki Protectora",
                capabilityId = "safety.collision_telemetry",
                domain = "Safety",
                riskLevel = "SAFETY_CRITICAL",
                physicalModelDescription = "Escudo cinemático de desaceleración y telemetría inercial extrema",
            ),
            CapabilityPackDetail(
                name = "Despacho SOS Sayayin",
                capabilityId = "sos.authoritative_dispatch",
                domain = "Emergency",
                riskLevel = "SAFETY_CRITICAL",
                physicalModelDescription = "Rutas de rescate prioritarias y enlace directo con auxilio 24/7",
            ),
        ),
        isFree = true,
        requiredEntitlement = null,
        priceFiatCrc = 0L,
        badgeTag = "DESBLOQUEADO",
        voiceSampleText = "¡Siento el Ki de este vehículo al máximo nivel! ¡Ningún obstáculo en el camino podrá detenernos!",
        avatarVisualType = "SAIYAN_SSJ4",
        themeColorHex = 0xFFFF1744L,
    )

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
        priceFiatCrc = 0L,
        badgeTag = "DESBLOQUEADO",
        voiceSampleText = "¡Hola! Soy EVAIR. Tu espíritu compañero en cada kilómetro.",
        avatarVisualType = "EVAIR_SPIRIT",
        themeColorHex = 0xFF00E5FFL,
    )

    val MASTER_MECHANIC = AgentManifest(
        id = "agent.master_mechanic",
        displayName = "Vanguard Mecha",
        subtitle = "Master Mechanic & Forense OBD",
        description = "Mecha de combate y diagnóstico: análisis profundo de DTCs, Mode $01, Mode $06, trim de combustible, guías de reparación y auditoría antifraude de cotizaciones de taller.",
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
        isFree = true,
        requiredEntitlement = null,
        priceFiatCrc = 0L,
        badgeTag = "DESBLOQUEADO",
        voiceSampleText = "Sistemas Mecha online. Escaneo de sensores completado sin errores.",
        avatarVisualType = "CYBER_MECHA",
        themeColorHex = 0xFFFF9100L,
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
        priceFiatCrc = 0L,
        badgeTag = "DESBLOQUEADO",
        voiceSampleText = "Soy Laya Celestial. El camino está despejado. Guiando tus pasos con precisión absoluta.",
        avatarVisualType = "LAYA_VALKYRIE",
        themeColorHex = 0xFFBB00FFL,
    )

    val VANGUARD_SENTINEL = SAIYAN_SSJ4
    val MOBILITY_PRIME = POKEMON_VOLT
    val EMISSIONS_SPECIALIST = DRACO_DRAGON

    val ALL = listOf(
        DRACO_DRAGON,
        POKEMON_VOLT,
        SAIYAN_SSJ4,
        EVAIR_CORE,
        MASTER_MECHANIC,
        LAYA_VALKYRIE,
    )
}
