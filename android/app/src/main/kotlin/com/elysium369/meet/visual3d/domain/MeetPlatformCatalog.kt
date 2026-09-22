package com.elysium369.meet.visual3d.domain

enum class PlatformVisualMaturity {
    REALISTIC_REFERENCE,
    PROCEDURAL_CONCEPT
}

data class MeetPlatformProfile(
    val id: String,
    val displayName: String,
    val category: String,
    val assetPath: String,
    val permanent: Boolean = false,
    val originalMeetDesign: Boolean = true,
    val visualMaturity: PlatformVisualMaturity = PlatformVisualMaturity.PROCEDURAL_CONCEPT
)

object MeetPlatformCatalog {
    val profiles = listOf(
        MeetPlatformProfile(
            id = "origin",
            displayName = "Elysium Origin",
            category = "Vehiculo base permanente",
            assetPath = "models/vehicle_twin/reference_vehicle.glb",
            permanent = true,
            originalMeetDesign = false,
            visualMaturity = PlatformVisualMaturity.REALISTIC_REFERENCE
        ),
        MeetPlatformProfile("titan_forge", "Elysium Titan Forge", "4x4 pesado original", "models/meet_platforms/titan_forge.glb"),
        MeetPlatformProfile("backhoe_hx", "Elysium Backhoe HX", "Retroexcavadora original", "models/meet_platforms/backhoe_hx.glb"),
        MeetPlatformProfile("terra_loader", "Elysium Terra Loader", "Cargador frontal original", "models/meet_platforms/terra_loader.glb"),
        MeetPlatformProfile("chronos_flux", "Elysium Chronos Flux", "Movilidad futura original", "models/meet_platforms/chronos_flux.glb"),
        MeetPlatformProfile("ion_vector", "Elysium Ion Vector", "Electrico original", "models/meet_platforms/ion_vector.glb"),
        MeetPlatformProfile("apex_r", "Elysium Apex R", "Superdeportivo original", "models/meet_platforms/apex_r.glb"),
        MeetPlatformProfile("aero_v1", "Elysium Aero V1", "Aeronave original", "models/meet_platforms/aero_v1.glb"),
        MeetPlatformProfile("asterion", "Elysium Asterion", "Cohete original", "models/meet_platforms/asterion.glb"),
        MeetPlatformProfile("abyss_one", "Elysium Abyss One", "Submarino original", "models/meet_platforms/abyss_one.glb")
    )

    val default = profiles.first()

    val realisticReferences = profiles.filter {
        it.visualMaturity == PlatformVisualMaturity.REALISTIC_REFERENCE
    }

    val conceptsAwaitingFinalMesh = profiles.filter {
        it.visualMaturity == PlatformVisualMaturity.PROCEDURAL_CONCEPT
    }

    fun requireById(id: String): MeetPlatformProfile =
        profiles.firstOrNull { it.id == id } ?: error("Unknown Elysium platform: $id")
}
