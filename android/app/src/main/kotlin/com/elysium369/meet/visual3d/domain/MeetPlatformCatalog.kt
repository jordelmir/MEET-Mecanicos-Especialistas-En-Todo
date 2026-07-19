package com.elysium369.meet.visual3d.domain

data class MeetPlatformProfile(
    val id: String,
    val displayName: String,
    val category: String,
    val assetPath: String,
    val permanent: Boolean = false,
    val originalMeetDesign: Boolean = true
)

object MeetPlatformCatalog {
    val profiles = listOf(
        MeetPlatformProfile(
            id = "origin",
            displayName = "MEET Origin",
            category = "Vehiculo base permanente",
            assetPath = "models/vehicle_twin/reference_vehicle.glb",
            permanent = true,
            originalMeetDesign = false
        ),
        MeetPlatformProfile("titan_forge", "MEET Titan Forge", "4x4 pesado original", "models/meet_platforms/titan_forge.glb"),
        MeetPlatformProfile("backhoe_hx", "MEET Backhoe HX", "Retroexcavadora original", "models/meet_platforms/backhoe_hx.glb"),
        MeetPlatformProfile("terra_loader", "MEET Terra Loader", "Cargador frontal original", "models/meet_platforms/terra_loader.glb"),
        MeetPlatformProfile("chronos_flux", "MEET Chronos Flux", "Movilidad futura original", "models/meet_platforms/chronos_flux.glb"),
        MeetPlatformProfile("ion_vector", "MEET Ion Vector", "Electrico original", "models/meet_platforms/ion_vector.glb"),
        MeetPlatformProfile("apex_r", "MEET Apex R", "Superdeportivo original", "models/meet_platforms/apex_r.glb"),
        MeetPlatformProfile("aero_v1", "MEET Aero V1", "Aeronave original", "models/meet_platforms/aero_v1.glb"),
        MeetPlatformProfile("asterion", "MEET Asterion", "Cohete original", "models/meet_platforms/asterion.glb"),
        MeetPlatformProfile("abyss_one", "MEET Abyss One", "Submarino original", "models/meet_platforms/abyss_one.glb")
    )

    val default = profiles.first()

    fun requireById(id: String): MeetPlatformProfile =
        profiles.firstOrNull { it.id == id } ?: error("Unknown MEET platform: $id")
}
