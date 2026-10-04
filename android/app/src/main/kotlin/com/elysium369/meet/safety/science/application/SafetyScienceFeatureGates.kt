package com.elysium369.meet.safety.science.application

/**
 * Feature gates for safety-science subsystem.
 *
 * ALL gates start `false` in production.
 * Gates must be toggled via remote config, NOT hardcoded.
 */
object SafetyScienceFeatureGates {

    /** Master kill switch for the entire scientific layer. */
    @Volatile var enabled: Boolean = false

    /** Allow claim creation. */
    @Volatile var claimsEnabled: Boolean = false

    /** Allow hypothesis creation and evaluation. */
    @Volatile var hypothesesEnabled: Boolean = false

    /** Allow research dataset creation. */
    @Volatile var researchEnabled: Boolean = false

    /** Allow publication workflow. */
    @Volatile var publicationEnabled: Boolean = false

    /** Allow AI claim extraction. */
    @Volatile var aiAssistantEnabled: Boolean = false

    /** Allow Merkle checkpoint signing. */
    @Volatile var checkpointSigningEnabled: Boolean = false

    /** Allow public projections. */
    @Volatile var publicProjectionsEnabled: Boolean = false
}
