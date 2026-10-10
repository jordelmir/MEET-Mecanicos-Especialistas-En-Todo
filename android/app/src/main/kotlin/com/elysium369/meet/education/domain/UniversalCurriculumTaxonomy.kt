package com.elysium369.meet.education.domain

import kotlinx.serialization.Serializable

/**
 * Universal Educational Domains defining the 10 core dimensions of human learning.
 * Structured to transcend physical school buildings and provide lifelong autonomous capability.
 */
enum class UniversalEducationalDomain(
    val code: String,
    val titleEs: String,
    val descriptionEs: String,
    val iscedBroadField: String,
) {
    DOMAIN_01_LANGUAGE(
        code = "LANG",
        titleEs = "Lenguaje, lectura y comunicación",
        descriptionEs = "Lectura comprensiva, escritura, ortografía, retórica, investigación, idiomas y alfabetización de adultos.",
        iscedBroadField = "01 Education / 02 Arts and Humanities",
    ),
    DOMAIN_02_MATHEMATICS(
        code = "MATH",
        titleEs = "Matemáticas, lógica y pensamiento científico",
        descriptionEs = "Aritmética, álgebra, geometría, cálculo, probabilidad, estadística, lógica formal y método científico.",
        iscedBroadField = "05 Natural sciences, mathematics and statistics",
    ),
    DOMAIN_03_NATURAL_SCIENCES(
        code = "SCI",
        titleEs = "Ciencias naturales y el universo",
        descriptionEs = "Física, química, biología, ecología, genética, astronomía, geología, salud preventiva y evidencia empírica.",
        iscedBroadField = "05 Natural sciences, mathematics and statistics",
    ),
    DOMAIN_04_HISTORY_SOCIETY(
        code = "SOC",
        titleEs = "Historia, geografía y sociedad",
        descriptionEs = "Historia mundial, geografía, sistemas políticos, derechos humanos, ciudadanía, instituciones y economía global.",
        iscedBroadField = "03 Social sciences, journalism and information",
    ),
    DOMAIN_05_FINANCE_LIVING(
        code = "FIN",
        titleEs = "Finanzas, economía y vida independiente",
        descriptionEs = "Presupuestos, ahorro, crédito, inversión, emprendimiento, contabilidad, contratos y derechos laborales.",
        iscedBroadField = "04 Business, administration and law",
    ),
    DOMAIN_06_TECHNOLOGY_AI(
        code = "TECH",
        titleEs = "Tecnología, informática e inteligencia artificial",
        descriptionEs = "Sistemas operativos, programación, redes, ciberseguridad, arquitectura de software e IA responsable.",
        iscedBroadField = "06 Information and Communication Technologies",
    ),
    DOMAIN_07_TRADES_ENGINEERING(
        code = "ENG",
        titleEs = "Oficios, ingeniería y habilidades prácticas",
        descriptionEs = "Electricidad, mecánica automotriz, electrónica, construcción, agricultura, herramientas y seguridad laboral.",
        iscedBroadField = "07 Engineering, manufacturing and construction",
    ),
    DOMAIN_08_ARTS_CREATIVITY(
        code = "ARTS",
        titleEs = "Arte, cultura y creatividad",
        descriptionEs = "Música, dibujo, literatura, diseño, cine, patrimonio cultural, pensamiento de diseño y obras originales.",
        iscedBroadField = "02 Arts and Humanities",
    ),
    DOMAIN_09_HUMAN_DEVELOPMENT(
        code = "HUMAN",
        titleEs = "Desarrollo humano y convivencia",
        descriptionEs = "Pensamiento crítico, metacognición, ética, empatía, resolución de conflictos, salud mental y bienestar.",
        iscedBroadField = "09 Health and welfare / Personal Development",
    ),
    DOMAIN_10_PROFESSIONAL_ADVANCED(
        code = "PROF",
        titleEs = "Formación profesional y conocimiento avanzado",
        descriptionEs = "Ciencias de la salud, derecho, investigación aplicada, especializaciones técnicas y educación continua.",
        iscedBroadField = "08 Agriculture / 09 Health / 10 Services",
    );
}

/**
 * Publication lifecycle status for verified educational entities.
 */
enum class ContentPublicationStatus {
    DRAFT,
    UNDER_REVIEW,
    PUBLISHED,
    RETIRED,
}

/**
 * Standard cognitive levels according to Revised Bloom's Taxonomy.
 */
enum class UniversalCognitiveLevel {
    REMEMBER,
    UNDERSTAND,
    APPLY,
    ANALYZE,
    EVALUATE,
    CREATE,
}

/**
 * Universal Competency entity representing an atomic, testable capability.
 */
@Serializable
data class UniversalCompetency(
    val id: String,
    val domain: UniversalEducationalDomain,
    val code: String,
    val title: String,
    val description: String,
    val cognitiveLevel: UniversalCognitiveLevel,
    val version: Int = 1,
    val status: ContentPublicationStatus = ContentPublicationStatus.PUBLISHED,
    val isFoundational: Boolean = false,
    val minimumEvidenceCount: Int = 3,
    val transferTaskRequired: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "Competency id cannot be blank" }
        require(code.isNotBlank()) { "Competency code cannot be blank" }
        require(version > 0) { "Competency version must be positive" }
        require(minimumEvidenceCount >= 1) { "Minimum evidence count must be at least 1" }
    }
}

/**
 * Universal Prerequisite edge defining dependencies in the curriculum knowledge graph.
 */
@Serializable
data class UniversalPrerequisite(
    val competencyId: String,
    val prerequisiteId: String,
    val isStrict: Boolean = true,
    val minRequiredMastery: Double = 0.70,
) {
    init {
        require(competencyId != prerequisiteId) { "Competency cannot depend on itself" }
        require(minRequiredMastery in 0.0..1.0) { "minRequiredMastery must be in [0.0, 1.0]" }
    }
}
