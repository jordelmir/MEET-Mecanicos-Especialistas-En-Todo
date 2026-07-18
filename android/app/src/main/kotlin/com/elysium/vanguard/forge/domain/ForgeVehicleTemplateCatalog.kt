package com.elysium.vanguard.forge.domain

enum class ForgeTemplateAuthority {
    CONCEPTUAL
}

enum class ForgePropulsionConcept {
    EV_AWD,
    PERFORMANCE_HYBRID,
    MID_ENGINE_COMBUSTION,
    COMPACT_SIX_CYLINDER,
    ELECTRIC_GRAND_TOURER
}

data class ForgeVehicleTemplate(
    val id: String,
    val name: String,
    val propulsion: ForgePropulsionConcept,
    val architecture: String,
    val designFocus: List<String>,
    val requiredSystems: Set<VehicleSystemType>,
    val authority: ForgeTemplateAuthority = ForgeTemplateAuthority.CONCEPTUAL
)

object ForgeVehicleTemplateCatalog {
    val templates: List<ForgeVehicleTemplate> = listOf(
        template(
            id = "aether_x1",
            name = "AETHER X1",
            propulsion = ForgePropulsionConcept.EV_AWD,
            architecture = "EV AWD con monocasco compuesto conceptual",
            focus = listOf("Aerodinámica activa", "Batería estructural", "Gestión térmica")
        ),
        template(
            id = "ignis_h1",
            name = "IGNIS H1",
            propulsion = ForgePropulsionConcept.PERFORMANCE_HYBRID,
            architecture = "Híbrido de alto desempeño conceptual",
            focus = listOf("Integración térmica-eléctrica", "Frenado regenerativo", "Refrigeración múltiple")
        ),
        template(
            id = "vortex_r10",
            name = "VORTEX R10",
            propulsion = ForgePropulsionConcept.MID_ENGINE_COMBUSTION,
            architecture = "Motor central longitudinal conceptual",
            focus = listOf("Rigidez torsional", "Lubricación bajo carga", "Transmisión y aerodinámica")
        ),
        template(
            id = "tempest_r6",
            name = "TEMPEST R6",
            propulsion = ForgePropulsionConcept.COMPACT_SIX_CYLINDER,
            architecture = "Seis cilindros compacto conceptual",
            focus = listOf("Balance masa-potencia", "Reparabilidad", "Manufactura modular")
        ),
        template(
            id = "obsidian_gtx",
            name = "OBSIDIAN GT-X",
            propulsion = ForgePropulsionConcept.ELECTRIC_GRAND_TOURER,
            architecture = "Gran turismo eléctrico conceptual",
            focus = listOf("Autonomía como objetivo pendiente", "Confort térmico y NVH", "Reparabilidad")
        )
    )

    fun find(id: String): ForgeVehicleTemplate? = templates.firstOrNull { it.id == id }

    fun createConceptVehicle(
        templateId: String,
        projectId: String,
        createdAt: Long
    ): ForgeVehicle {
        val template = requireNotNull(find(templateId)) { "Unknown FORGE vehicle template: $templateId" }
        require(projectId.isNotBlank()) { "projectId cannot be blank" }
        require(createdAt >= 0L) { "createdAt must be non-negative" }

        return ForgeVehicle(
            artifact = ForgeArtifact(
                id = projectId,
                name = template.name,
                description = "${template.architecture}. Estado conceptual; requiere ingeniería, pruebas y validación profesional.",
                artifactType = ForgeArtifactType.VEHICLE,
                createdAt = createdAt,
                updatedAt = createdAt,
                safetyClassification = SafetyClassification.REQUIRES_PROFESSIONAL_VALIDATION,
                tags = listOf("forge-template", template.id, template.authority.name.lowercase())
            ),
            rootAssemblyId = "asm_${projectId}_root",
            systems = template.requiredSystems.map { systemType ->
                VehicleSystemNode(
                    id = "${projectId}_${systemType.name.lowercase()}",
                    systemType = systemType,
                    assemblyId = "asm_${projectId}_${systemType.name.lowercase()}",
                    name = systemType.name,
                    isComplete = false
                )
            },
            powertrain = null,
            diagnosticProfile = null,
            simulationScenarios = emptyList()
        )
    }

    private fun template(
        id: String,
        name: String,
        propulsion: ForgePropulsionConcept,
        architecture: String,
        focus: List<String>
    ) = ForgeVehicleTemplate(
        id = id,
        name = name,
        propulsion = propulsion,
        architecture = architecture,
        designFocus = focus,
        requiredSystems = setOf(
            VehicleSystemType.CHASSIS,
            VehicleSystemType.BODY,
            VehicleSystemType.ENGINE,
            VehicleSystemType.TRANSMISSION,
            VehicleSystemType.SUSPENSION,
            VehicleSystemType.BRAKES,
            VehicleSystemType.STEERING,
            VehicleSystemType.COOLING,
            VehicleSystemType.ELECTRICAL,
            VehicleSystemType.INTERIOR,
            VehicleSystemType.SAFETY
        )
    )
}
