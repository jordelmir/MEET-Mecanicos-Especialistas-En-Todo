package com.elysium369.meet.education.economic

enum class SkillCredentialState {
    LEARNED,
    PRACTICED,
    DEMONSTRATED,
    ELYSIUM_VERIFIED,
    EXTERNALLY_CREDENTIALLED,
}

data class EconomicOccupation(
    val iscoCode: String,
    val title: String,
    val majorGroup: String,
    val submajorGroup: String,
    val description: String,
)

data class SkillToServiceMapping(
    val skillId: String,
    val iscoCode: String,
    val serviceVertical: String,
    val isRegulatedLicenseRequired: Boolean = false,
)

object SkillToServiceBridge {

    /**
     * Enforces the supreme constitutional invariant:
     * KNOWLEDGE != COMPETENCE != CREDENTIAL
     *
     * A completed simulation or course curriculum can grant DEMONSTRATED or ELYSIUM_VERIFIED,
     * but NEVER silently promotes to EXTERNALLY_CREDENTIALLED without external authoritative proof.
     */
    fun validateCredentialPromotion(
        current: SkillCredentialState,
        requested: SkillCredentialState,
        hasExternalLicenseEvidence: Boolean,
        isSimulationOutcomeOnly: Boolean,
    ): SkillCredentialState {
        if (requested == SkillCredentialState.EXTERNALLY_CREDENTIALLED) {
            if (isSimulationOutcomeOnly || !hasExternalLicenseEvidence) {
                throw IllegalStateException(
                    "CONSTITUTIONAL_VIOLATION: Simulation completion cannot grant external professional license without verified external credentials."
                )
            }
        }
        return requested
    }

    /**
     * Canonical mappings for 7.º Artes Industriales (Fontanería) bridging into Elysium Services.
     */
    val CANONICAL_PLUMBING_MAPPINGS: List<SkillToServiceMapping> = listOf(
        SkillToServiceMapping(
            skillId = "cr_font7_s_pvc_assembly",
            iscoCode = "7126",
            serviceVertical = "RESIDENTIAL_PLUMBING",
            isRegulatedLicenseRequired = false,
        ),
        SkillToServiceMapping(
            skillId = "cr_font7_s_valve_repair",
            iscoCode = "7126",
            serviceVertical = "RESIDENTIAL_PLUMBING",
            isRegulatedLicenseRequired = false,
        ),
        SkillToServiceMapping(
            skillId = "cr_art_s_vistas",
            iscoCode = "3118",
            serviceVertical = "ARCHITECTURAL_AND_TECHNICAL_CAD",
            isRegulatedLicenseRequired = false,
        ),
        SkillToServiceMapping(
            skillId = "cr_art_s_circuito_simple",
            iscoCode = "7411",
            serviceVertical = "RESIDENTIAL_ELECTRICAL_SERVICES",
            isRegulatedLicenseRequired = false,
        ),
        SkillToServiceMapping(
            skillId = "cr_fis_s_vehicular_dynamics",
            iscoCode = "7231",
            serviceVertical = "AUTOMOTIVE_MECHANICAL_DIAGNOSTICS",
            isRegulatedLicenseRequired = false,
        )
    )
}
