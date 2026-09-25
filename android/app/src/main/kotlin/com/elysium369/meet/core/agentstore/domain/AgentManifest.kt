package com.elysium369.meet.core.agentstore.domain

import kotlinx.serialization.Serializable

enum class AgentCategory {
    CORE,
    AUTOMOTIVE,
    EMISSIONS,
    MOBILITY,
    SAFETY,
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
data class AgentManifest(
    val id: String,
    val version: String = "1.0.0",
    val displayName: String,
    val description: String,
    val category: AgentCategory,
    val avatarAssetId: String,
    val voiceProfileId: String,
    val personalityProfileId: String,
    val capabilityIds: List<String>,
    val knowledgePackIds: List<String> = emptyList(),
    val minimumAppVersion: String = "4.26.0",
    val requiredEntitlement: String? = null,
    val isFree: Boolean = (requiredEntitlement == null),
    val priceFiatCrc: Long = 0L,
    val safetyPolicyVersion: String = "2026.1",
    val privacyDescription: String = "Opera exclusivamente con datos locales o autorizados explícitamente.",
    val supportedLanguages: List<String> = listOf("es", "en"),
    val releaseState: AgentReleaseState = AgentReleaseState.OFFICIAL,
)

/**
 * Standard roster of official Elysium agents.
 * Master Order Omega §30, §31, §32, §33.
 */
object OfficialAgents {

    val EVAIR_CORE = AgentManifest(
        id = "agent.evair_core",
        displayName = "EVAIR Core",
        description = "Asistente universal inteligente para navegación, modo enseñanza y orquestación general de la plataforma.",
        category = AgentCategory.CORE,
        avatarAssetId = "avatar_evair_living_core",
        voiceProfileId = "voice_evair_synth_v1",
        personalityProfileId = "personality_evair_helpful",
        capabilityIds = listOf(
            "guide.start_tutorial",
            "ride.request",
        ),
        isFree = true,
        requiredEntitlement = null,
        priceFiatCrc = 0L,
    )

    val MASTER_MECHANIC = AgentManifest(
        id = "agent.master_mechanic",
        displayName = "Master Mechanic",
        description = "Inteligencia automotriz avanzada para análisis profundo de DTCs, Mode \$01, Mode \$06, trim de combustible, guías de reparación y salud mecánica.",
        category = AgentCategory.AUTOMOTIVE,
        avatarAssetId = "avatar_master_mechanic",
        voiceProfileId = "voice_mechanic_pro",
        personalityProfileId = "personality_mechanic_rigorous",
        capabilityIds = listOf(
            "vehicle.read_dtc",
            "vehicle.mode06",
            "vehicle.fuel_trim",
            "vehicle.freeze_frame",
        ),
        isFree = false,
        requiredEntitlement = "agent.master_mechanic",
        priceFiatCrc = 7_500L,
    )

    val EMISSIONS_SPECIALIST = AgentManifest(
        id = "agent.emissions_specialist",
        displayName = "Emissions Specialist",
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
        isFree = false,
        requiredEntitlement = "agent.emissions_specialist",
        priceFiatCrc = 5_000L,
    )

    val ALL = listOf(EVAIR_CORE, MASTER_MECHANIC, EMISSIONS_SPECIALIST)
}
