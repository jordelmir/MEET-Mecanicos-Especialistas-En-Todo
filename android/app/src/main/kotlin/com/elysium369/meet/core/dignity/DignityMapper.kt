package com.elysium369.meet.core.dignity

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import com.elysium369.meet.education.economic.SkillCredentialState
import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  D I G N I T Y   M A P P E R
 *  ──────────────────────────────
 *  ASTRA V6 §85 — "Todos somos capaces de ser dignos."
 *
 *  The most profound problem: people don't know what they know.
 *  A mother of 20 years has logistics, nutrition, time management,
 *  conflict resolution, and pedagogy skills.
 *  A farmer has agronomy, practical meteorology, machinery mechanics.
 *
 *  ELYSIUM must DISCOVER what someone can offer, even when that
 *  person believes "I don't know how to do anything."
 *
 *  This engine maps life experiences to marketable skills,
 *  always with honest confidence levels.
 * ══════════════════════════════════════════════════════════════════════
 */

// ─── Life Experience → Skill Mapping ───

@Serializable
data class LifeExperience(
    val id: String,
    val category: ExperienceCategory,
    val description: String,
    val yearsOfExperience: Int,
    val context: String = "",
)

enum class ExperienceCategory(val label: String) {
    PARENTING("Crianza y familia"),
    FARMING("Agricultura y campo"),
    COOKING("Cocina y alimentación"),
    CAREGIVING("Cuidado de personas"),
    CONSTRUCTION("Construcción informal"),
    DRIVING("Conducción y transporte"),
    SELLING("Venta ambulante o informal"),
    CRAFTING("Artesanía y manualidades"),
    COMMUNITY("Liderazgo comunitario"),
    SPORTS("Deportes y actividad física"),
    TECHNOLOGY("Tecnología autodidacta"),
    MUSIC("Música y arte"),
    LANGUAGES("Idiomas"),
    MILITARY("Servicio militar"),
    VOLUNTEERING("Voluntariado"),
    OTHER("Otra experiencia"),
}

@Serializable
data class DiscoveredSkill(
    val skillName: String,
    val domain: UniversalServiceDomain,
    val sourceExperience: ExperienceCategory,
    val transferConfidence: TransferConfidence,
    val estimatedCredentialState: SkillCredentialState,
    val marketDescription: String,
    val requiresVerification: Boolean = true,
) {
    /**
     * INVARIANT: Never claim EXACT competence from life experience alone.
     * Always honest about the gap between informal and formal.
     */
    val disclaimer: String = when (transferConfidence) {
        TransferConfidence.HIGH ->
            "Habilidad probable basada en experiencia extensa. Requiere demostración práctica."
        TransferConfidence.MODERATE ->
            "Habilidad posible basada en experiencia relacionada. Requiere verificación."
        TransferConfidence.LOW ->
            "Habilidad potencial detectada. Requiere capacitación y verificación."
        TransferConfidence.SPECULATIVE ->
            "Potencial detectado pero no verificable sin evaluación directa."
    }
}

enum class TransferConfidence {
    HIGH,           // 10+ years, directly applicable
    MODERATE,       // 5+ years or partially transferable
    LOW,            // Some related experience
    SPECULATIVE,    // Possible but unverified
}

// ─── Dignity Profile ───

@Serializable
data class DignityProfile(
    val userId: String,
    val displayName: String,
    val lifeExperiences: List<LifeExperience> = emptyList(),
    val discoveredSkills: List<DiscoveredSkill> = emptyList(),
    val selfAssessmentComplete: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) {
    val totalDiscoveredSkills: Int get() = discoveredSkills.size
    val highConfidenceSkills: List<DiscoveredSkill>
        get() = discoveredSkills.filter { it.transferConfidence == TransferConfidence.HIGH }
    val marketableSkills: List<DiscoveredSkill>
        get() = discoveredSkills.filter {
            it.transferConfidence in listOf(TransferConfidence.HIGH, TransferConfidence.MODERATE)
        }
}

// ─── The Mapper Engine ───

object DignityMapper {

    /**
     * Maps life experiences to discoverable, marketable skills.
     * This is the engine that tells someone "you ARE capable."
     *
     * INVARIANT: Never fabricate skills. Every mapping has evidence
     * (the life experience) and honest confidence levels.
     */
    fun discoverSkills(experiences: List<LifeExperience>): List<DiscoveredSkill> {
        return experiences.flatMap { exp -> mapExperienceToSkills(exp) }
    }

    private fun mapExperienceToSkills(exp: LifeExperience): List<DiscoveredSkill> {
        val confidence = when {
            exp.yearsOfExperience >= 10 -> TransferConfidence.HIGH
            exp.yearsOfExperience >= 5 -> TransferConfidence.MODERATE
            exp.yearsOfExperience >= 2 -> TransferConfidence.LOW
            else -> TransferConfidence.SPECULATIVE
        }

        val credentialState = when (confidence) {
            TransferConfidence.HIGH -> SkillCredentialState.PRACTICED
            TransferConfidence.MODERATE -> SkillCredentialState.PRACTICED
            TransferConfidence.LOW -> SkillCredentialState.LEARNED
            TransferConfidence.SPECULATIVE -> SkillCredentialState.LEARNED
        }

        return EXPERIENCE_SKILL_MAP[exp.category]?.map { mapping ->
            DiscoveredSkill(
                skillName = mapping.skillName,
                domain = mapping.domain,
                sourceExperience = exp.category,
                transferConfidence = confidence,
                estimatedCredentialState = credentialState,
                marketDescription = mapping.marketDescription,
            )
        } ?: emptyList()
    }

    /**
     * Builds a full dignity profile from life experiences.
     */
    fun buildProfile(
        userId: String,
        displayName: String,
        experiences: List<LifeExperience>,
    ): DignityProfile {
        val skills = discoverSkills(experiences)
        return DignityProfile(
            userId = userId,
            displayName = displayName,
            lifeExperiences = experiences,
            discoveredSkills = skills,
            selfAssessmentComplete = experiences.isNotEmpty(),
        )
    }

    // ─── Experience → Skill Mapping Table ───

    private data class SkillMapping(
        val skillName: String,
        val domain: UniversalServiceDomain,
        val marketDescription: String,
    )

    private val EXPERIENCE_SKILL_MAP: Map<ExperienceCategory, List<SkillMapping>> = mapOf(
        ExperienceCategory.PARENTING to listOf(
            SkillMapping("Gestión del tiempo", UniversalServiceDomain.TUTORING,
                "Organización de horarios y logística familiar"),
            SkillMapping("Nutrición práctica", UniversalServiceDomain.CUSTOM,
                "Planificación de alimentación saludable y económica"),
            SkillMapping("Pedagogía informal", UniversalServiceDomain.TUTORING,
                "Enseñanza y apoyo escolar para niños"),
            SkillMapping("Mediación de conflictos", UniversalServiceDomain.CUSTOM,
                "Resolución pacífica de disputas y negociación"),
        ),
        ExperienceCategory.FARMING to listOf(
            SkillMapping("Agronomía práctica", UniversalServiceDomain.CUSTOM,
                "Cultivo, siembra y manejo de cosechas"),
            SkillMapping("Mecánica de maquinaria", UniversalServiceDomain.AUTO_MECHANICAL,
                "Mantenimiento y reparación de equipos agrícolas"),
            SkillMapping("Meteorología aplicada", UniversalServiceDomain.CUSTOM,
                "Lectura de patrones climáticos para agricultura"),
        ),
        ExperienceCategory.COOKING to listOf(
            SkillMapping("Cocina profesional", UniversalServiceDomain.EVENTS,
                "Preparación de alimentos a escala"),
            SkillMapping("Catering y eventos", UniversalServiceDomain.EVENTS,
                "Servicio de alimentación para eventos"),
            SkillMapping("Nutrición aplicada", UniversalServiceDomain.TUTORING,
                "Asesoría en alimentación balanceada"),
        ),
        ExperienceCategory.CAREGIVING to listOf(
            SkillMapping("Cuidado geriátrico", UniversalServiceDomain.CUSTOM,
                "Atención y acompañamiento de adultos mayores"),
            SkillMapping("Primeros auxilios", UniversalServiceDomain.CUSTOM,
                "Respuesta básica ante emergencias de salud"),
            SkillMapping("Acompañamiento terapéutico", UniversalServiceDomain.CUSTOM,
                "Apoyo emocional y compañía estructurada"),
        ),
        ExperienceCategory.CONSTRUCTION to listOf(
            SkillMapping("Albañilería", UniversalServiceDomain.CUSTOM,
                "Construcción, reparación y acabados en concreto"),
            SkillMapping("Plomería básica", UniversalServiceDomain.PLUMBING,
                "Instalación y reparación de tuberías"),
            SkillMapping("Electricidad residencial", UniversalServiceDomain.ELECTRICAL,
                "Cableado, tomacorrientes e instalación eléctrica"),
            SkillMapping("Pintura", UniversalServiceDomain.PAINTING,
                "Pintura interior/exterior y acabados"),
        ),
        ExperienceCategory.DRIVING to listOf(
            SkillMapping("Transporte de personas", UniversalServiceDomain.PASSENGER_RIDE,
                "Conducción segura y servicio al cliente"),
            SkillMapping("Mensajería y entregas", UniversalServiceDomain.COURIER,
                "Entrega rápida y confiable de paquetes"),
            SkillMapping("Logística de rutas", UniversalServiceDomain.MOVING,
                "Planificación de rutas eficientes"),
        ),
        ExperienceCategory.SELLING to listOf(
            SkillMapping("Ventas y negociación", UniversalServiceDomain.CUSTOM,
                "Técnicas de venta, atención al cliente, cierre"),
            SkillMapping("Gestión de inventario", UniversalServiceDomain.CUSTOM,
                "Control de stock y aprovisionamiento"),
        ),
        ExperienceCategory.CRAFTING to listOf(
            SkillMapping("Artesanía", UniversalServiceDomain.CUSTOM,
                "Creación de productos artesanales únicos"),
            SkillMapping("Diseño y creatividad", UniversalServiceDomain.GRAPHIC_DESIGN,
                "Diseño visual y composición artística"),
        ),
        ExperienceCategory.COMMUNITY to listOf(
            SkillMapping("Liderazgo comunitario", UniversalServiceDomain.EVENTS,
                "Organización de grupos y gestión de proyectos"),
            SkillMapping("Mediación comunitaria", UniversalServiceDomain.CUSTOM,
                "Resolución de conflictos vecinales"),
        ),
        ExperienceCategory.TECHNOLOGY to listOf(
            SkillMapping("Soporte técnico", UniversalServiceDomain.SOFTWARE,
                "Resolución de problemas de computadoras y celulares"),
            SkillMapping("Redes sociales", UniversalServiceDomain.SOFTWARE,
                "Gestión de presencia digital para negocios"),
        ),
        ExperienceCategory.LANGUAGES to listOf(
            SkillMapping("Traducción", UniversalServiceDomain.TRANSLATION,
                "Traducción escrita y oral entre idiomas"),
            SkillMapping("Tutoría de idiomas", UniversalServiceDomain.TUTORING,
                "Enseñanza conversacional de idiomas"),
        ),
    )
}
