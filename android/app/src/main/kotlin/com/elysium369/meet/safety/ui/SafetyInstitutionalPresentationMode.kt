package com.elysium369.meet.safety.ui

/**
 * Institutional Presentation Navigator for Parliamentary & Public Authority Demonstrations.
 *
 * Implements ADR-003:
 * Allows demonstrating Elysium Safety to the Legislative Assembly of Costa Rica
 * focused exclusively on citizen security, digital evidence, and territorial analysis,
 * while preserving the complete codebase, diagnostics, mobility, and marketplace intact.
 */
enum class PresentationMode {
    FULL_PLATFORM_OPERATING_SYSTEM,
    INSTITUTIONAL_DEPUTIES_SAFETY,
}

enum class PlatformModuleDestination {
    SAFETY_CITIZEN_CORE,
    AUTOMOTIVE_OBD_DIAGNOSTICS,
    MOBILITY_DISPATCH,
    PARTS_MARKETPLACE,
    VEHICLE_HISTORY,
}

data class SafetyPresentationSection(
    val sectionNumber: Int,
    val title: String,
    val description: String,
    val demonstratedStatus: String,
)

object SafetyInstitutionalPresentationMode {

    const val PARLIAMENTARY_OPENING_STATEMENT =
        "Elysium Safety no pretende reemplazar a las autoridades ni decidir quién es culpable. " +
            "Pretende resolver un problema anterior: cómo capturar, preservar, estructurar y analizar " +
            "información de seguridad para que la evidencia no se pierda y pueda llegar a las " +
            "instituciones competentes con trazabilidad."

    val institutionalSections = listOf(
        SafetyPresentationSection(
            sectionNumber = 1,
            title = "Reportar incidentes de seguridad",
            description = "Estructura reportes sobre asaltos, homicidios, violencia, narcotráfico y emergencias.",
            demonstratedStatus = "DEMOSTRADA",
        ),
        SafetyPresentationSection(
            sectionNumber = 2,
            title = "Adjuntar y organizar evidencia",
            description = "Fotografías, videos, documentos y procedencia enlazados de forma estructurada.",
            demonstratedStatus = "DEMOSTRADA",
        ),
        SafetyPresentationSection(
            sectionNumber = 3,
            title = "Georreferenciar los acontecimientos",
            description = "Visualización territorial de eventos con preservación de privacidad y blur residencial.",
            demonstratedStatus = "DEMOSTRADA",
        ),
        SafetyPresentationSection(
            sectionNumber = 4,
            title = "Mantener trazabilidad de la información",
            description = "Diferenciación estricta entre reporte ciudadano y confirmación oficial de autoridad.",
            demonstratedStatus = "DEMOSTRADA",
        ),
        SafetyPresentationSection(
            sectionNumber = 5,
            title = "Construir una cadena de evidencia",
            description = "Hashes SHA-256 inmutables, códigos QR, protocolo SAFETY-CUSTODY-V2 y firmas Ed25519.",
            demonstratedStatus = "DEMOSTRADA",
        ),
        SafetyPresentationSection(
            sectionNumber = 6,
            title = "Crear inteligencia territorial",
            description = "Tendencias y análisis geoespacial para orientar recursos institucionales.",
            demonstratedStatus = "DEMOSTRADA",
        ),
        SafetyPresentationSection(
            sectionNumber = 7,
            title = "Facilitar colaboración ciudadano-institución",
            description = "Pasarela de enlace con Fuerza Pública, OIJ, Ministerio Público y Poder Judicial.",
            demonstratedStatus = "PENDIENTE_DE_PILOTO",
        ),
    )

    /**
     * Resolves accessible platform modules for the current presentation mode.
     */
    fun resolveAllowedModules(mode: PresentationMode): List<PlatformModuleDestination> {
        return when (mode) {
            PresentationMode.FULL_PLATFORM_OPERATING_SYSTEM -> listOf(
                PlatformModuleDestination.SAFETY_CITIZEN_CORE,
                PlatformModuleDestination.AUTOMOTIVE_OBD_DIAGNOSTICS,
                PlatformModuleDestination.MOBILITY_DISPATCH,
                PlatformModuleDestination.PARTS_MARKETPLACE,
                PlatformModuleDestination.VEHICLE_HISTORY,
            )
            PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY -> listOf(
                PlatformModuleDestination.SAFETY_CITIZEN_CORE,
            )
        }
    }

    /**
     * Checks if a module is visible in the given mode.
     */
    fun isModuleVisible(module: PlatformModuleDestination, mode: PresentationMode): Boolean {
        return module in resolveAllowedModules(mode)
    }
}
