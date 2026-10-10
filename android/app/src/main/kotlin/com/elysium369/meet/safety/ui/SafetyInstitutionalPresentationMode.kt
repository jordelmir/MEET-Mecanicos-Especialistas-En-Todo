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
    const val PLATFORM_TITLE = "Elysium Safety"
    const val PLATFORM_SUBTITLE = "Plataforma tecnológica para seguridad ciudadana, evidencia y análisis territorial"
    const val PLATFORM_SUMMARY =
        "Elysium Safety es un sistema tecnológico orientado a fortalecer la capacidad de ciudadanos e instituciones " +
            "para documentar, organizar, preservar, georreferenciar y analizar información relacionada con incidentes de seguridad."
    const val INSTITUTIONAL_MISSION_STATEMENT =
        "La propuesta no busca sustituir a la Fuerza Pública, el Ministerio Público, el Organismo de Investigación Judicial " +
            "ni al Poder Judicial. Busca proporcionar una capa tecnológica de información y trazabilidad que facilite el trabajo de las instituciones competentes."

    const val PARLIAMENTARY_OPENING_STATEMENT =
        "Elysium Safety no pretende reemplazar a las autoridades ni decidir quién es culpable. " +
            "Pretende resolver un problema anterior: cómo capturar, preservar, estructurar y analizar " +
            "información de seguridad para que la evidencia no se pierda y pueda llegar a las " +
            "instituciones competentes con trazabilidad."

    val CORE_PRINCIPLE_PIPELINE = listOf(
        "capturar",
        "preservar",
        "organizar",
        "georreferenciar",
        "relacionar",
        "verificar",
        "analizar",
    )

    const val CORE_PRINCIPLE_TEXT =
        "Elysium Safety no pretende reemplazar una investigación judicial. Su función es proporcionar " +
            "infraestructura tecnológica para capturar → preservar → organizar → georreferenciar → relacionar → verificar → analizar " +
            "información relacionada con seguridad. La plataforma debe entenderse como una herramienta de apoyo, no como una autoridad que determina culpabilidad."

    val POTENTIAL_COSTA_RICA_PILLARS = listOf(
        "Participación ciudadana.",
        "Evidencia digital estructurada.",
        "Inteligencia territorial.",
        "Trazabilidad.",
        "Análisis de patrones.",
        "Interoperabilidad institucional.",
        "Preservación de información.",
        "Mejor organización de información previa a una investigación formal.",
    )

    const val POTENTIAL_COSTA_RICA_CLOSING =
        "El objetivo final es que información que actualmente puede permanecer fragmentada en teléfonos, redes sociales, " +
            "conversaciones o archivos pueda convertirse en información estructurada, trazable y potencialmente útil para las autoridades competentes.\n\n" +
            "Elysium Safety propone tecnología para que la información no se pierda."

    val institutionalSections = listOf(
        SafetyPresentationSection(
            sectionNumber = 1,
            title = "Reportar incidentes de seguridad",
            description = "Estructura información sobre acontecimientos como asaltos, homicidios, desapariciones, situaciones sospechosas, violencia, narcotráfico y emergencias, asociados con ubicación, fecha, hora, descripción y material de respaldo.",
            demonstratedStatus = "DEMOSTRADA",
        ),
        SafetyPresentationSection(
            sectionNumber = 2,
            title = "Adjuntar y organizar evidencia",
            description = "Incorpora fotografías y documentos locales (PDF), videos mediante enlaces web (YouTube, Drive, redes sociales para proteger el servidor), georreferenciación y testimonios para evitar dispersión de datos.",
            demonstratedStatus = "DEMOSTRADA",
        ),
        SafetyPresentationSection(
            sectionNumber = 3,
            title = "Georreferenciar los acontecimientos",
            description = "Visualización territorial mediante mapas para identificar dónde ocurren los eventos, concentraciones geográficas, patrones espaciales y zonas prioritarias de atención institucional.",
            demonstratedStatus = "DEMOSTRADA",
        ),
        SafetyPresentationSection(
            sectionNumber = 4,
            title = "Mantener trazabilidad de la información",
            description = "Distinción formal entre estados de conocimiento (OBSERVED → AUTHORITATIVE → DERIVED → ESTIMATED → UNKNOWN), separando claramente 'Una persona reportó' de 'La autoridad confirmó'.",
            demonstratedStatus = "DEMOSTRADA",
        ),
        SafetyPresentationSection(
            sectionNumber = 5,
            title = "Construir una cadena de evidencia",
            description = "Relaciona Evento → Afirmación → Hipótesis → Evidencia → Análisis → Conclusión con hashes SHA-256 sobre archivos originales y códigos QR de verificación inmutable.",
            demonstratedStatus = "DEMOSTRADA",
        ),
        SafetyPresentationSection(
            sectionNumber = 6,
            title = "Crear inteligencia territorial",
            description = "Transforma información dispersa en datos estructurados para analizar incremento de incidentes, repetición de eventos, evolución temporal y relación espacial entre acontecimientos.",
            demonstratedStatus = "DEMOSTRADA",
        ),
        SafetyPresentationSection(
            sectionNumber = 7,
            title = "Facilitar colaboración ciudadano-institución",
            description = "Puente formal: Ciudadano → Evidencia → Información estructurada → Institución competente (Fuerza Pública, OIJ, Ministerio Público y Poder Judicial).",
            demonstratedStatus = "DEMOSTRADA",
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
