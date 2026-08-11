package com.elysium369.meet.core.obd

/**
 * Compatibility boundary for the retired hard-coded active-command catalog.
 *
 * RoutineControl (0x31) and InputOutputControlByIdentifier (0x2F) identifiers
 * are OEM-, ECU-, software- and vehicle-specific. MEET must never present an
 * invented generic command as executable authority. Production capabilities
 * are supplied only by reviewed, vehicle-applicable capability packs and run
 * through [ActiveDiagnosticSafetyKernel].
 */
object DiagnosticCommandManager {

    data class ObdCommandDef(
        val name: String,
        val description: String,
        val command: String,
        val expectedResponse: String,
        val category: String,
        val manufacturer: String = "GENERIC",
        val capabilityPackId: String? = null,
        val targetAddress: String? = null,
    )

    /** No hard-coded command is authorized in production. */
    fun getCommandsByCategory(
        @Suppress("UNUSED_PARAMETER") category: String,
        @Suppress("UNUSED_PARAMETER") manufacturer: String = "GENERIC",
    ): List<ObdCommandDef> = emptyList()

    /** Names are presentation only and can never resolve executable commands. */
    fun getCommandByName(@Suppress("UNUSED_PARAMETER") name: String): ObdCommandDef? = null
}
