package com.elysium369.meet.education.domain

import kotlinx.serialization.Serializable

/**
 * OECD PISA (Programme for International Student Assessment) Core Domains.
 */
enum class PisaDomain(
    val code: String,
    val titleEs: String,
    val descriptionEs: String,
) {
    MATHEMATICAL_LITERACY(
        code = "PISA_MATH",
        titleEs = "Competencia Matemática PISA (OECD)",
        descriptionEs = "Capacidad para razonar matemáticamente y formular, emplear e interpretar matemáticas para resolver problemas en una variedad de contextos del mundo real.",
    ),
    READING_LITERACY(
        code = "PISA_READING",
        titleEs = "Competencia Lectora PISA (OECD)",
        descriptionEs = "Capacidad para comprender, utilizar, evaluar, reflexionar y comprometerse con textos con el fin de alcanzar metas personales, desarrollar el conocimiento y participar en la sociedad.",
    ),
    SCIENTIFIC_LITERACY(
        code = "PISA_SCIENCE",
        titleEs = "Competencia Científica PISA (OECD)",
        descriptionEs = "Capacidad para comprometerse con cuestiones relacionadas con la ciencia y con las ideas de la ciencia como un ciudadano reflexivo (explicar fenómenos, evaluar indagaciones e interpretar pruebas).",
    ),
    CREATIVE_THINKING(
        code = "PISA_CREATIVE",
        titleEs = "Pensamiento Creativo e Innovador (OECD)",
        descriptionEs = "Competencia para participar productivamente en la generación, evaluación y mejora de ideas que conducen a soluciones originales y eficaces.",
    ),
    LEARNING_IN_DIGITAL_WORLD(
        code = "PISA_DIGITAL",
        titleEs = "Aprendizaje en el Mundo Digital e Inteligencia Artificial (OECD)",
        descriptionEs = "Capacidad para autorregular el aprendizaje en entornos digitales ricos en información y utilizar herramientas computacionales de forma crítica.",
    );
}

/**
 * OECD PISA Proficiency Levels (Levels 1b to 6).
 * Based on the OECD PISA 500-point scale (mean 500, SD 100).
 */
enum class PisaProficiencyLevel(
    val levelNumber: Int,
    val minScore: Int,
    val maxScore: Int,
    val titleEs: String,
    val descriptionEs: String,
    val isOecdBaseline: Boolean, // Level 2 is the OECD baseline for functional participation in society
) {
    BELOW_LEVEL_1(
        levelNumber = 0,
        minScore = 0,
        maxScore = 357,
        titleEs = "Por debajo del Nivel 1",
        descriptionEs = "No demuestra las habilidades más elementales evaluadas por PISA. Requiere intervención pedagógica emergente.",
        isOecdBaseline = false,
    ),
    LEVEL_1B(
        levelNumber = 1,
        minScore = 358,
        maxScore = 419,
        titleEs = "Nivel 1b (Muy Básico)",
        descriptionEs = "Puede responder preguntas en contextos sumamente familiares donde toda la información relevante está explícitamente presente.",
        isOecdBaseline = false,
    ),
    LEVEL_1A(
        levelNumber = 2,
        minScore = 420,
        maxScore = 481,
        titleEs = "Nivel 1a (Básico Inicial)",
        descriptionEs = "Puede responder preguntas que involucran instrucciones directas en situaciones explícitas y realizar acciones obvias.",
        isOecdBaseline = false,
    ),
    LEVEL_2(
        levelNumber = 3,
        minScore = 482,
        maxScore = 544,
        titleEs = "Nivel 2 (Umbral Mínimo OCDE de Competencia)",
        descriptionEs = "UMBRAL CRÍTICO DE LA OCDE: Comienza a demostrar la capacidad para emplear conocimientos en situaciones que exigen inferencias directas y razonamiento funcional en la vida moderna.",
        isOecdBaseline = true,
    ),
    LEVEL_3(
        levelNumber = 4,
        minScore = 545,
        maxScore = 606,
        titleEs = "Nivel 3 (Competencia Moderada)",
        descriptionEs = "Puede ejecutar procedimientos descritos claramente, seleccionar e integrar diferentes representaciones y formular interpretaciones sencillas.",
        isOecdBaseline = false,
    ),
    LEVEL_4(
        levelNumber = 5,
        minScore = 607,
        maxScore = 668,
        titleEs = "Nivel 4 (Competencia Alta)",
        descriptionEs = "Trabaja eficazmente con modelos explícitos en situaciones complejas. Puede seleccionar e integrar diferentes representaciones vinculándolas directamente a situaciones del mundo real.",
        isOecdBaseline = false,
    ),
    LEVEL_5(
        levelNumber = 6,
        minScore = 669,
        maxScore = 730,
        titleEs = "Nivel 5 (Rendimiento Superior)",
        descriptionEs = "Desarrolla y trabaja con modelos en situaciones complejas, identificando restricciones y especificando supuestos. Aplica estrategias bien desarrolladas de resolución de problemas.",
        isOecdBaseline = false,
    ),
    LEVEL_6(
        levelNumber = 7,
        minScore = 731,
        maxScore = 1000,
        titleEs = "Nivel 6 (Excelencia de Clase Mundial)",
        descriptionEs = "Conceptualiza, generaliza y utiliza información basada en investigaciones y modelos de situaciones complejas. Demuestra pensamiento y razonamiento científico y matemático avanzado.",
        isOecdBaseline = false,
    );

    companion object {
        fun fromScore(score: Int): PisaProficiencyLevel {
            return entries.firstOrNull { score in it.minScore..it.maxScore }
                ?: if (score < 358) BELOW_LEVEL_1 else LEVEL_6
        }
    }
}

/**
 * PISA Contexts of Application.
 */
enum class PisaContext(val titleEs: String) {
    PERSONAL("Personal (vida cotidiana, familia, pares)"),
    OCCUPATIONAL("Ocupacional (mundo del trabajo, oficios, ingeniería)"),
    SOCIETAL("Social (comunidad local, medios, sociedad, economía)"),
    SCIENTIFIC("Científico (fenómenos naturales, tecnología, investigación)"),
}

/**
 * PISA Cognitive Process categories across domains.
 */
enum class PisaCognitiveProcess(val domain: PisaDomain, val titleEs: String, val descriptionEs: String) {
    // Mathematics processes (PISA 2022)
    MATH_FORMULATE(PisaDomain.MATHEMATICAL_LITERACY, "Formular situaciones matemáticamente", "Traducir un problema del mundo real a estructura matemática formal."),
    MATH_EMPLOY(PisaDomain.MATHEMATICAL_LITERACY, "Emplear conceptos, datos y procedimientos", "Aplicar algoritmos, fórmulas y cálculo para obtener soluciones."),
    MATH_INTERPRET_EVALUATE(PisaDomain.MATHEMATICAL_LITERACY, "Interpretar y evaluar resultados", "Reflexionar si la solución matemática tiene sentido en el contexto real original."),
    MATH_REASON(PisaDomain.MATHEMATICAL_LITERACY, "Razonar matemáticamente", "Construir argumentos, justificar hipótesis y conectar conceptos abstractos."),

    // Reading processes (PISA 2018/2025)
    READING_LOCATE_INFORMATION(PisaDomain.READING_LITERACY, "Localizar información", "Acceder y recuperar datos específicos en textos continuos y discontinuos."),
    READING_UNDERSTAND_INTEGRATE(PisaDomain.READING_LITERACY, "Comprender e integrar", "Construir el significado global y realizar inferencias coherentes."),
    READING_EVALUATE_REFLECT(PisaDomain.READING_LITERACY, "Evaluar y reflexionar", "Juzgar la credibilidad de fuentes, detectar sesgos, desinformación y conflictos entre textos múltiples."),

    // Science processes (PISA 2025)
    SCIENCE_EXPLAIN_PHENOMENA(PisaDomain.SCIENTIFIC_LITERACY, "Explicar fenómenos científicamente", "Reconocer, ofrecer y evaluar explicaciones de una gama de fenómenos naturales y tecnológicos."),
    SCIENCE_EVALUATE_DESIGN_ENQUIRY(PisaDomain.SCIENTIFIC_LITERACY, "Evaluar y diseñar investigaciones científicas", "Describir y evaluar indagaciones, distinguir preguntas científicas y proponer formas de controlar variables."),
    SCIENCE_INTERPRET_DATA_EVIDENCE(PisaDomain.SCIENTIFIC_LITERACY, "Interpretar datos y pruebas científicamente", "Analizar y evaluar datos científicos, afirmaciones y argumentos en una variedad de representaciones."),
}

/**
 * Atomic PISA Item definition representing a real-world scenario.
 */
@Serializable
data class PisaScenarioItem(
    val id: String,
    val domain: PisaDomain,
    val targetLevel: PisaProficiencyLevel,
    val process: String,
    val context: PisaContext,
    val scenarioPrompt: String,
    val stimulusMaterialUrl: String? = null,
    val correctResponseLogic: String,
    val commonMisconceptions: Map<String, String> = emptyMap(),
)
