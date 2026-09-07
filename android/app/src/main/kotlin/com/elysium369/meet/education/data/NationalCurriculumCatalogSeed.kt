package com.elysium369.meet.education.data

import com.elysium369.meet.education.domain.EpistemicTruthState

enum class CurriculumTrack(
    val displayName: String,
    val cycleName: String,
    val gradeNumber: Int,
    val subjectName: String
) {
    // ── I CICLO (1.º, 2.º, 3.º Primaria) ───────────────────────────────────
    MATEMATICA_1("1.º Matemática", "I Ciclo", 1, "MATEMATICA"),
    MATEMATICA_2("2.º Matemática", "I Ciclo", 2, "MATEMATICA"),
    MATEMATICA_3("3.º Matemática", "I Ciclo", 3, "MATEMATICA"),
    CIENCIAS_PRIMARIA("Ciencias Primaria", "I y II Ciclos", 1, "CIENCIAS"),
    ESPANOL_PRIMARIA("Español Primaria", "I y II Ciclos", 1, "ESPANOL"),
    ESTUDIOS_SOCIALES_PRIMARIA("Estudios Sociales Primaria", "I y II Ciclos", 1, "ESTUDIOS_SOCIALES"),
    INGLES_PRIMARIA("Inglés Primaria", "I y II Ciclos", 1, "INGLES"),

    // ── II CICLO (4.º, 5.º, 6.º Primaria) ──────────────────────────────────
    MATEMATICA_4("4.º Matemática", "II Ciclo", 4, "MATEMATICA"),
    MATEMATICA_5("5.º Matemática", "II Ciclo", 5, "MATEMATICA"),
    MATEMATICA_6("6.º Matemática", "II Ciclo", 6, "MATEMATICA"),

    // ── III CICLO (7.º, 8.º, 9.º Secundaria / EGB) ─────────────────────────
    MATEMATICA_7("7.º Matemática (Zapandí)", "III Ciclo", 7, "MATEMATICA"),
    FONTANERIA_7("7.º Fontanería (Artes Ind.)", "III Ciclo", 7, "ARTES_INDUSTRIALES"),
    ESPANOL_7("7.º Español", "III Ciclo", 7, "ESPANOL"),
    CIENCIAS_7("7.º Ciencias", "III Ciclo", 7, "CIENCIAS"),
    ESTUDIOS_SOCIALES_7("7.º Estudios Sociales", "III Ciclo", 7, "ESTUDIOS_SOCIALES"),
    CIVICA_7("7.º Educación Cívica", "III Ciclo", 7, "EDUCACION_CIVICA"),
    INGLES_7("7.º Inglés", "III Ciclo", 7, "INGLES"),

    MATEMATICA_8("8.º Matemática (Ujarrás)", "III Ciclo", 8, "MATEMATICA"),
    DIBUJO_TECNICO_8("8.º Dibujo Técnico (CAD)", "III Ciclo", 8, "ARTES_INDUSTRIALES"),
    ESPANOL_8("8.º Español", "III Ciclo", 8, "ESPANOL"),
    CIENCIAS_8("8.º Ciencias", "III Ciclo", 8, "CIENCIAS"),
    ESTUDIOS_SOCIALES_8("8.º Estudios Sociales", "III Ciclo", 8, "ESTUDIOS_SOCIALES"),
    CIVICA_8("8.º Educación Cívica", "III Ciclo", 8, "EDUCACION_CIVICA"),
    INGLES_8("8.º Inglés", "III Ciclo", 8, "INGLES"),

    MATEMATICA_9("9.º Matemática (Tárcoles)", "III Ciclo", 9, "MATEMATICA"),
    ELECTRICIDAD_9("9.º Electricidad Residencial", "III Ciclo", 9, "ARTES_INDUSTRIALES"),
    ESPANOL_9("9.º Español", "III Ciclo", 9, "ESPANOL"),
    CIENCIAS_9("9.º Ciencias", "III Ciclo", 9, "CIENCIAS"),
    ESTUDIOS_SOCIALES_9("9.º Estudios Sociales", "III Ciclo", 9, "ESTUDIOS_SOCIALES"),
    CIVICA_9("9.º Educación Cívica", "III Ciclo", 9, "EDUCACION_CIVICA"),
    INGLES_9("9.º Inglés", "III Ciclo", 9, "INGLES"),
    CIENCIAS_III_CICLO("Ciencias III Ciclo", "III Ciclo", 7, "CIENCIAS"),

    // ── DIVERSIFICADA & BACHILLERATO POR MADUREZ (10.º - 11.º / BxM) ───────
    MATEMATICA_BXM("Matemática (BxM 10.º-11.º)", "Diversificada", 11, "MATEMATICA"),
    ESPANOL_BXM("Español (BxM 10.º-11.º)", "Diversificada", 11, "ESPANOL"),
    BIOLOGIA_BXM("Biología (BxM 10.º-11.º)", "Diversificada", 10, "BIOLOGIA"),
    QUIMICA_BXM("Química (BxM 10.º-11.º)", "Diversificada", 11, "QUIMICA"),
    FISICA_BXM("Física (BxM 10.º-11.º)", "Diversificada", 11, "FISICA"),
    SOCIALES_BXM("Estudios Sociales (BxM)", "Diversificada", 11, "ESTUDIOS_SOCIALES"),
    CIVICA_BXM("Educación Cívica (BxM)", "Diversificada", 11, "EDUCACION_CIVICA"),
    INGLES_BXM("Inglés (BxM 10.º-11.º)", "Diversificada", 11, "INGLES");

    val isPrimary: Boolean get() = gradeNumber in 1..6
    val isSecondaryBasic: Boolean get() = gradeNumber in 7..9
    val isDiversifiedOrAdult: Boolean get() = gradeNumber >= 10
}

object NationalCurriculumCatalogSeed {

    fun getUnitsForTrack(track: CurriculumTrack): List<CourseUnitData> {
        return when (track) {
            CurriculumTrack.MATEMATICA_1 -> CourseZeroCurriculumSeed.MATEMATICA_1_UNITS
            CurriculumTrack.FONTANERIA_7 -> CourseZeroCurriculumSeed.FONTANERIA_7_UNITS
            CurriculumTrack.MATEMATICA_2 -> MATEMATICA_2_UNITS
            CurriculumTrack.MATEMATICA_3 -> MATEMATICA_3_UNITS
            CurriculumTrack.MATEMATICA_4 -> MATEMATICA_4_UNITS
            CurriculumTrack.MATEMATICA_5 -> MATEMATICA_5_UNITS
            CurriculumTrack.MATEMATICA_6 -> MATEMATICA_6_UNITS
            CurriculumTrack.CIENCIAS_PRIMARIA -> CIENCIAS_PRIMARIA_UNITS
            CurriculumTrack.ESPANOL_PRIMARIA -> ESPANOL_PRIMARIA_UNITS
            CurriculumTrack.ESTUDIOS_SOCIALES_PRIMARIA -> ESTUDIOS_SOCIALES_PRIMARIA_UNITS
            CurriculumTrack.INGLES_PRIMARIA -> INGLES_PRIMARIA_UNITS
            CurriculumTrack.MATEMATICA_7 -> MATEMATICA_7_UNITS
            CurriculumTrack.ESPANOL_7 -> ESPANOL_7_UNITS
            CurriculumTrack.CIENCIAS_7 -> CIENCIAS_7_UNITS
            CurriculumTrack.ESTUDIOS_SOCIALES_7 -> ESTUDIOS_SOCIALES_7_UNITS
            CurriculumTrack.CIVICA_7 -> CIVICA_7_UNITS
            CurriculumTrack.INGLES_7 -> INGLES_7_UNITS
            CurriculumTrack.MATEMATICA_8 -> MATEMATICA_8_UNITS
            CurriculumTrack.DIBUJO_TECNICO_8 -> DIBUJO_TECNICO_8_UNITS
            CurriculumTrack.ESPANOL_8 -> ESPANOL_8_UNITS
            CurriculumTrack.CIENCIAS_8 -> CIENCIAS_8_UNITS
            CurriculumTrack.ESTUDIOS_SOCIALES_8 -> ESTUDIOS_SOCIALES_8_UNITS
            CurriculumTrack.CIVICA_8 -> CIVICA_8_UNITS
            CurriculumTrack.INGLES_8 -> INGLES_8_UNITS
            CurriculumTrack.MATEMATICA_9 -> MATEMATICA_9_UNITS
            CurriculumTrack.ELECTRICIDAD_9 -> ELECTRICIDAD_9_UNITS
            CurriculumTrack.ESPANOL_9 -> ESPANOL_9_UNITS
            CurriculumTrack.CIENCIAS_9 -> CIENCIAS_9_UNITS
            CurriculumTrack.ESTUDIOS_SOCIALES_9 -> ESTUDIOS_SOCIALES_9_UNITS
            CurriculumTrack.CIVICA_9 -> CIVICA_9_UNITS
            CurriculumTrack.INGLES_9 -> INGLES_9_UNITS
            CurriculumTrack.CIENCIAS_III_CICLO -> CIENCIAS_III_CICLO_UNITS
            CurriculumTrack.MATEMATICA_BXM -> MATEMATICA_BXM_UNITS
            CurriculumTrack.ESPANOL_BXM -> ESPANOL_BXM_UNITS
            CurriculumTrack.BIOLOGIA_BXM -> BIOLOGIA_BXM_UNITS
            CurriculumTrack.QUIMICA_BXM -> QUIMICA_BXM_UNITS
            CurriculumTrack.FISICA_BXM -> FISICA_BXM_UNITS
            CurriculumTrack.SOCIALES_BXM -> SOCIALES_BXM_UNITS
            CurriculumTrack.CIVICA_BXM -> CIVICA_BXM_UNITS
            CurriculumTrack.INGLES_BXM -> INGLES_BXM_UNITS
        }
    }

    val ALL_UNITS: List<CourseUnitData> by lazy {
        CurriculumTrack.values().flatMap { getUnitsForTrack(it) }
    }

    // ── 2.º AÑO MATEMÁTICA ───────────────────────────────────────────────────
    val MATEMATICA_2_UNITS = listOf(
        CourseUnitData(
            id = "cr_mat2_u01",
            track = CurriculumTrack.MATEMATICA_2,
            unitNumber = 1,
            targetMonth = 2,
            monthName = "Febrero",
            title = "Números Naturales hasta 1000 y Centenas",
            description = "Conteo, valor posicional y descomposición aditiva en centenas, decenas y unidades.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat2_c_centenas",
                    conceptCode = "CR_MAT2_CENTENAS",
                    title = "Centenas, Decenas y Unidades hasta 1000",
                    description = "Comprensión del valor posicional en base 10 hasta el 1000.",
                    targetMonth = 2,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat2_centenas_desc",
                            conceptId = "cr_mat2_c_centenas",
                            title = "Descomposición de Centenas",
                            prompt = "En la bodega de repuestos hay 348 bujías. ¿Cómo se descompone este número?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "3 centenas, 4 decenas y 8 unidades (300 + 40 + 8)",
                                "34 centenas y 8 decenas",
                                "3 decenas y 48 unidades",
                                "8 centenas, 4 decenas y 3 unidades"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El dígito 3 está en las centenas (300), el 4 en las decenas (40) y el 8 en las unidades (8)."
                        ),
                        InteractiveTaskData(
                            id = "task_mat2_transfer_centenas",
                            conceptId = "cr_mat2_c_centenas",
                            title = "Comparación de Precios de Taller",
                            prompt = "Un filtro de aceite cuesta ₡675 en el local A y ₡765 en el local B. ¿Cuál afirmación es correcta?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "₡675 < ₡765 porque 6 centenas es menor que 7 centenas",
                                "₡675 > ₡765 porque 75 es mayor que 65",
                                "Son iguales porque tienen los mismos dígitos",
                                "₡765 es menor porque empieza con 7"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Al comparar las centenas, 6 centenas (600) es estrictamente menor que 7 centenas (700)."
                        )
                    )
                )
            )
        )
    )

    // ── 3.º AÑO MATEMÁTICA ───────────────────────────────────────────────────
    val MATEMATICA_3_UNITS = listOf(
        CourseUnitData(
            id = "cr_mat3_u01",
            track = CurriculumTrack.MATEMATICA_3,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Multiplicación Fundamental y Tablas",
            description = "Arreglos rectangulares, producto y propiedades multiplicativas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat3_c_multiplicacion",
                    conceptCode = "CR_MAT3_MULTIPLICACION",
                    title = "Multiplicación como Suma Abreviada",
                    description = "Modelado de arreglos rectangulares y algoritmos de multiplicación.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat3_mult_repuestos",
                            conceptId = "cr_mat3_c_multiplicacion",
                            title = "Cajas de Fusibles",
                            prompt = "Un mecánico compra 6 paquetes con 8 fusibles cada uno. ¿Cuántos fusibles tiene en total?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("48 fusibles (6 × 8)", "14 fusibles (6 + 8)", "42 fusibles", "56 fusibles"),
                            correctOptionIndex = 0,
                            explanation = "6 grupos de 8 corresponden a 6 × 8 = 48."
                        )
                    )
                )
            )
        )
    )

    // ── 4.º AÑO MATEMÁTICA ───────────────────────────────────────────────────
    val MATEMATICA_4_UNITS = listOf(
        CourseUnitData(
            id = "cr_mat4_u01",
            track = CurriculumTrack.MATEMATICA_4,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "División y Fracciones Homogéneas",
            description = "Reparto equitativo, fracciones propias e impropias y suma con igual denominador.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat4_c_fracciones",
                    conceptCode = "CR_MAT4_FRACCIONES",
                    title = "Fracciones Homogéneas y Representación",
                    description = "Concepto de fracción unitaria, numerador y denominador.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat4_frac_tanque",
                            conceptId = "cr_mat4_c_fracciones",
                            title = "Nivel de Combustible",
                            prompt = "El indicador de un automóvil marca 3/8 de tanque en la mañana. Al mediodía se agregan 2/8. ¿Cuánto combustible hay ahora?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("5/8 de tanque", "5/16 de tanque", "1/8 de tanque", "6/8 de tanque"),
                            correctOptionIndex = 0,
                            explanation = "En fracciones homogéneas se suman los numeradores (3 + 2 = 5) y se conserva el denominador (8)."
                        )
                    )
                )
            )
        )
    )

    // ── 5.º AÑO MATEMÁTICA ───────────────────────────────────────────────────
    val MATEMATICA_5_UNITS = listOf(
        CourseUnitData(
            id = "cr_mat5_u01",
            track = CurriculumTrack.MATEMATICA_5,
            unitNumber = 1,
            targetMonth = 4,
            monthName = "Abril",
            title = "Decimales y Proporcionalidad Directa",
            description = "Operaciones con decimales y regla de tres simple.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat5_c_proporcionalidad",
                    conceptCode = "CR_MAT5_PROPORCIONALIDAD",
                    title = "Proporcionalidad Directa y Regla de Tres",
                    description = "Relaciones directamente proporcionales en problemas de consumo y precios.",
                    targetMonth = 4,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat5_regla_tres_gas",
                            conceptId = "cr_mat5_c_proporcionalidad",
                            title = "Consumo de Gasolina",
                            prompt = "Un vehículo consume 4 litros de gasolina para recorrer 50 km. ¿Cuántos litros consumirá en 150 km a velocidad constante?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("12 litros", "8 litros", "16 litros", "10 litros"),
                            correctOptionIndex = 0,
                            explanation = "150 km es el triple de 50 km (150/50 = 3), por lo que el consumo es el triple: 4 × 3 = 12 litros."
                        )
                    )
                )
            )
        )
    )

    // ── 6.º AÑO MATEMÁTICA ───────────────────────────────────────────────────
    val MATEMATICA_6_UNITS = listOf(
        CourseUnitData(
            id = "cr_mat6_u01",
            track = CurriculumTrack.MATEMATICA_6,
            unitNumber = 1,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Porcentajes y Estadística Descriptiva",
            description = "Tanto por ciento, descuento, IVA y análisis de gráficos estadísticos.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat6_c_porcentajes",
                    conceptCode = "CR_MAT6_PORCENTAJES",
                    title = "Cálculo de Porcentajes e IVA",
                    description = "Cálculo de porcentajes en contextos comerciales y facturación.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat6_iva_cr",
                            conceptId = "cr_mat6_c_porcentajes",
                            title = "Cálculo del IVA en Costa Rica (13%)",
                            prompt = "Un servicio de cambio de aceite cuesta ₡20.000 sin impuesto. ¿Cuánto es el 13% de IVA?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("₡2.600", "₡1.300", "₡2.000", "₡3.000"),
                            correctOptionIndex = 0,
                            explanation = "20.000 × 0.13 = ₡2.600."
                        )
                    )
                )
            )
        )
    )

    // ── CIENCIAS PRIMARIA ────────────────────────────────────────────────────
    val CIENCIAS_PRIMARIA_UNITS = listOf(
        CourseUnitData(
            id = "cr_cien_pri_u01",
            track = CurriculumTrack.CIENCIAS_PRIMARIA,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Ecosistemas y Biodiversidad de Costa Rica",
            description = "Factores bióticos y abióticos, cadenas alimentarias y parques nacionales.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cien_c_ecosistemas",
                    conceptCode = "CR_CIEN_ECOSISTEMAS",
                    title = "Ecosistemas y Redes Tróficas",
                    description = "Productores, consumidores y descomponedores en el trópico.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cien_pri_red_trofica",
                            conceptId = "cr_cien_c_ecosistemas",
                            title = "Cadena Trófica en el Bosque Nuboso",
                            prompt = "¿Cuál organismo actúa como productor primario en el ecosistema de Monteverde?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("El helecho arbóreo (planta)", "El quetzal", "El jaguar", "El hongo descomponedor"),
                            correctOptionIndex = 0,
                            explanation = "Las plantas realizan la fotosíntesis y constituyen el primer eslabón productor de la cadena."
                        )
                    )
                )
            )
        )
    )

    // ── ESPAÑOL PRIMARIA ─────────────────────────────────────────────────────
    val ESPANOL_PRIMARIA_UNITS = listOf(
        CourseUnitData(
            id = "cr_esp_pri_u01",
            track = CurriculumTrack.ESPANOL_PRIMARIA,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Comprensión Lectora e Inferencia",
            description = "Estrategias de lectura, ideas principales y producción de textos coherentes.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp_c_comprension",
                    conceptCode = "CR_ESP_COMPRENSION",
                    title = "Identificación de Ideas Principales",
                    description = "Distinción entre tema, idea principal y detalles secundarios.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp_pri_idea_central",
                            conceptId = "cr_esp_c_comprension",
                            title = "La Idea Principal",
                            prompt = "En un texto sobre la seguridad vial: ¿Cuál es la idea principal de un párrafo que explica el uso del cinturón?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "El cinturón de seguridad reduce drásticamente el riesgo de lesiones mortales en colisiones.",
                                "Los cinturones son de color negro o gris.",
                                "Los automóviles modernos tienen asientos cómodos.",
                                "A algunas personas se les olvida abrochárselo."
                            ),
                            correctOptionIndex = 0,
                            explanation = "La idea principal sintetiza la función vital y el argumento central del párrafo."
                        )
                    )
                )
            )
        )
    )

    // ── 7.º AÑO MATEMÁTICA (ZAPANDÍ) ─────────────────────────────────────────
    val MATEMATICA_7_UNITS = listOf(
        CourseUnitData(
            id = "cr_mat7_u01",
            track = CurriculumTrack.MATEMATICA_7,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Números Enteros (Z) y Leyes de Signos",
            description = "Operaciones en Z, valor absoluto y resolución de problemas con temperaturas y alturas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat7_c_enteros",
                    conceptCode = "CR_MAT7_ENTEROS",
                    title = "Operaciones y Leyes de Signos en Z",
                    description = "Suma, resta, multiplicación y división en números enteros.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat7_signos_calc",
                            conceptId = "cr_mat7_c_enteros",
                            title = "Ley de Signos en Multiplicación",
                            prompt = "¿Cuál es el resultado de calcular (-6) × (-4) + (-10)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("14", "-34", "-14", "34"),
                            correctOptionIndex = 0,
                            explanation = "(-6) × (-4) = +24. Luego 24 + (-10) = 24 - 10 = 14."
                        )
                    )
                )
            )
        )
    )

    // ── 8.º AÑO MATEMÁTICA (UJARRÁS) ─────────────────────────────────────────
    val MATEMATICA_8_UNITS = listOf(
        CourseUnitData(
            id = "cr_mat8_u01",
            track = CurriculumTrack.MATEMATICA_8,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Números Racionales (Q) y Álgebra",
            description = "Operaciones en Q, monomios semejantes y expresiones algebraicas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat8_c_algebra",
                    conceptCode = "CR_MAT8_ALGEBRA",
                    title = "Monomios y Reducción de Términos Semejantes",
                    description = "Operaciones con coeficientes y factores literales en álgebra.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat8_monomios_red",
                            conceptId = "cr_mat8_c_algebra",
                            title = "Reducción de Monomios",
                            prompt = "¿Cuál es el resultado de simplificar 5x²y - 8x²y + 2x²y?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("-x²y", "-x⁴y²", "15x²y", "-5x²y"),
                            correctOptionIndex = 0,
                            explanation = "(5 - 8 + 2)x²y = -1x²y = -x²y."
                        )
                    )
                )
            )
        )
    )

    // ── 8.º AÑO DIBUJO TÉCNICO (CAD) ─────────────────────────────────────────
    val DIBUJO_TECNICO_8_UNITS = listOf(
        CourseUnitData(
            id = "cr_art_ind_u02",
            track = CurriculumTrack.DIBUJO_TECNICO_8,
            unitNumber = 1,
            targetMonth = 6,
            monthName = "Junio",
            title = "Dibujo Técnico y Metrología Dimensional",
            description = "Escalímetro, vistas ortogonales, escalas arquitectónicas y planos técnicos (ISCO 3118).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_art_c_dibujo_tecnico",
                    conceptCode = "CR_ART_DIBUJO_TECNICO",
                    title = "Metrología y Lectura de Vistas Ortogonales",
                    description = "Interpretación de vistas frontal, superior y lateral en piezas mecánicas y estructuras.",
                    targetMonth = 6,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_art8_escala_plano",
                            conceptId = "cr_art_c_dibujo_tecnico",
                            title = "Lectura de Escala 1:50",
                            prompt = "En un plano de taller a escala 1:50, una línea mide 6 cm. ¿Cuál es su dimensión real en metros?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("3 metros (6 × 0.5 m)", "12 metros", "30 metros", "1.5 metros"),
                            correctOptionIndex = 0,
                            explanation = "En escala 1:50, cada centímetro en el plano representa 50 cm (0.5 m) en la realidad: 6 × 0.5 m = 3 metros."
                        )
                    )
                )
            )
        )
    )

    // ── 9.º AÑO MATEMÁTICA (TÁRCOLES) ────────────────────────────────────────
    val MATEMATICA_9_UNITS = listOf(
        CourseUnitData(
            id = "cr_mat9_u01",
            track = CurriculumTrack.MATEMATICA_9,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Números Reales (R) y Productos Notables",
            description = "Radicales, cuadrado del binomio, factorización e introducción a funciones.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat9_c_productos_notables",
                    conceptCode = "CR_MAT9_PRODUCTOS_NOTABLES",
                    title = "Productos Notables y Factorización",
                    description = "(a + b)² = a² + 2ab + b² y diferencia de cuadrados.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat9_binomio_cuad",
                            conceptId = "cr_mat9_c_productos_notables",
                            title = "Desarrollo del Binomio al Cuadrado",
                            prompt = "¿Cuál es el desarrollo correcto de (2x + 3)²?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("4x² + 12x + 9", "4x² + 9", "4x² + 6x + 9", "2x² + 12x + 9"),
                            correctOptionIndex = 0,
                            explanation = "(2x)² + 2(2x)(3) + 3² = 4x² + 12x + 9."
                        )
                    )
                )
            )
        )
    )

    // ── 9.º AÑO ELECTRICIDAD RESIDENCIAL ─────────────────────────────────────
    val ELECTRICIDAD_9_UNITS = listOf(
        CourseUnitData(
            id = "cr_art_ind_u03",
            track = CurriculumTrack.ELECTRICIDAD_9,
            unitNumber = 1,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Electricidad Residencial y Seguridad Eléctrica",
            description = "Ley de Ohm, circuitos de iluminación, multímetro y código eléctrico (ISCO 7411).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_art_c_electricidad",
                    conceptCode = "CR_ART_ELECTRICIDAD",
                    title = "Instalación y Verificación de Circuitos Eléctricos",
                    description = "Medición de voltaje, cableado simple y protocolo de puesta a tierra.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_art9_ohm_calc",
                            conceptId = "cr_art_c_electricidad",
                            title = "Cálculo de Corriente (Ley de Ohm)",
                            prompt = "Una bombilla automotriz de 12V tiene una resistencia de 4 Ohms. ¿Cuántos amperios de corriente consumirá?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("3 Amperios (I = V / R = 12 / 4)", "48 Amperios", "0.33 Amperios", "8 Amperios"),
                            correctOptionIndex = 0,
                            explanation = "Según la Ley de Ohm, I = V / R = 12 V / 4 Ω = 3 A."
                        )
                    )
                )
            )
        )
    )

    // ── CIENCIAS III CICLO ───────────────────────────────────────────────────
    val CIENCIAS_III_CICLO_UNITS = listOf(
        CourseUnitData(
            id = "cr_cien_iii_u01",
            track = CurriculumTrack.CIENCIAS_III_CICLO,
            unitNumber = 1,
            targetMonth = 4,
            monthName = "Abril",
            title = "Estructura Celular y Sistemas del Cuerpo",
            description = "Organelas celulares, microscopía, nutrición y metabolismo.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cien_c_celula",
                    conceptCode = "CR_CIEN_CELULA",
                    title = "Organelas Celulares y Respiración Celular",
                    description = "Funciones de la mitocondria, núcleo y membrana celular.",
                    targetMonth = 4,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cien_iii_mitocondria",
                            conceptId = "cr_cien_c_celula",
                            title = "La Central Energética Celular",
                            prompt = "¿Cuál organela celular es la encargada principal de producir energía (ATP) mediante la respiración celular?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Mitocondria", "Ribosoma", "Aparato de Golgi", "Vacuola"),
                            correctOptionIndex = 0,
                            explanation = "Las mitocondrias son las organelas encargadas de la respiración celular y síntesis de ATP."
                        )
                    )
                )
            )
        )
    )

    // ── MATEMÁTICA BACHILLERATO POR MADUREZ (10.º - 11.º) ───────────────────
    val MATEMATICA_BXM_UNITS = listOf(
        CourseUnitData(
            id = "cr_mat_bxm_u01",
            track = CurriculumTrack.MATEMATICA_BXM,
            unitNumber = 1,
            targetMonth = 2,
            monthName = "Febrero",
            title = "Geometría Analítica: La Circunferencia",
            description = "Ecuación de la circunferencia (x-h)² + (y-k)² = r², rectas secantes, tangentes y exteriores.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat_bxm_c_circunferencia",
                    conceptCode = "CR_MAT_BXM_CIRCUNFERENCIA",
                    title = "Ecuación y Rectas en la Circunferencia",
                    description = "Centro, radio y posiciones relativas de rectas y puntos.",
                    targetMonth = 2,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat_bxm_centro_radio",
                            conceptId = "cr_mat_bxm_c_circunferencia",
                            title = "Centro y Radio de la Circunferencia",
                            prompt = "Dada la ecuación (x - 3)² + (y + 5)² = 49, ¿cuál es el centro C y el radio r?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Centro (3, -5) y radio r = 7",
                                "Centro (-3, 5) y radio r = 49",
                                "Centro (3, 5) y radio r = 7",
                                "Centro (-3, -5) y radio r = 7"
                            ),
                            correctOptionIndex = 0,
                            explanation = "En (x - h)² + (y - k)² = r², h = 3, k = -5 y r = √49 = 7."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat_bxm_u02",
            track = CurriculumTrack.MATEMATICA_BXM,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Funciones Exponenciales y Logarítmicas",
            description = "Modelado de crecimiento exponencial, función inversa y propiedades logarítmicas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat_bxm_c_funciones",
                    conceptCode = "CR_MAT_BXM_FUNCIONES",
                    title = "Modelado Exponencial y Logarítmico",
                    description = "Análisis de funciones crecientes y decrecientes f(x) = a^x.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat_bxm_exp_crecimiento",
                            conceptId = "cr_mat_bxm_c_funciones",
                            title = "Crecimiento Exponencial",
                            prompt = "Una función f(x) = (1.5)^x es exponencial con base a = 1.5. ¿Cómo se comporta su gráfica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Es estrictamente creciente en todo su dominio porque a > 1",
                                "Es estrictamente decreciente porque 1.5 es un decimal",
                                "Es una línea recta horizontal",
                                "Pasa por el punto (0, 0)"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Cuando la base a > 1 en f(x) = a^x, la función exponencial es estrictamente creciente."
                        )
                    )
                )
            )
        )
    )

    // ── ESPAÑOL BXM ──────────────────────────────────────────────────────────
    val ESPANOL_BXM_UNITS = listOf(
        CourseUnitData(
            id = "cr_esp_bxm_u01",
            track = CurriculumTrack.ESPANOL_BXM,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Análisis Literario y Vicios del Lenguaje",
            description = "Lecturas canónicas del MEP, tipos de narrador, figuras retóricas y corrección idiomática.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp_bxm_c_analisis_literario",
                    conceptCode = "CR_ESP_BXM_ANALISIS_LITERARIO",
                    title = "Análisis del Narrador y Figuras Retóricas",
                    description = "Narrador omnisciente, protagonista, testigo y figuras literarias.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp_bxm_narrador",
                            conceptId = "cr_esp_bxm_c_analisis_literario",
                            title = "Identificación de Narrador Omnisciente",
                            prompt = "\"Sabía bien lo que él pensaba en ese instante y conocía el destino que les aguardaba a todos\". ¿Qué tipo de narrador se evidencia?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Narrador omnisciente (conoce pensamientos y sentimientos de los personajes)",
                                "Narrador protagonista (habla en primera persona)",
                                "Narrador testigo (solo relata lo que ve desde afuera)",
                                "Narrador en segunda persona"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El narrador omnisciente todo lo sabe, incluso los pensamientos íntimos y el futuro de los personajes."
                        )
                    )
                )
            )
        )
    )

    // ── BIOLOGÍA BXM ─────────────────────────────────────────────────────────
    val BIOLOGIA_BXM_UNITS = listOf(
        CourseUnitData(
            id = "cr_bio_bxm_u01",
            track = CurriculumTrack.BIOLOGIA_BXM,
            unitNumber = 1,
            targetMonth = 4,
            monthName = "Abril",
            title = "Genética Mendeliana y Herencia Biológica",
            description = "Leyes de Mendel, cuadros de Punnett, genotipos y fenotipos.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_bio_bxm_c_genetica",
                    conceptCode = "CR_BIO_BXM_GENETICA",
                    title = "Cruces Monohíbridos y Cuadros de Punnett",
                    description = "Determinación de proporciones genotípicas y fenotípicas mendelianas.",
                    targetMonth = 4,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_bio_bxm_cruce_hetero",
                            conceptId = "cr_bio_bxm_c_genetica",
                            title = "Cruce de Dos Individuos Heterocigotos (Aa × Aa)",
                            prompt = "En un cruce monohíbrido entre dos plantas heterocigotas (Aa × Aa), ¿cuál es la probabilidad fenotípica del rasgo dominante?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "75% dominante (3/4)",
                                "50% dominante (2/4)",
                                "100% dominante",
                                "25% dominante (1/4)"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El cuadro de Punnett da 1 AA : 2 Aa : 1 aa. Tanto AA como Aa expresan el fenotipo dominante (3 de 4 = 75%)."
                        )
                    )
                )
            )
        )
    )

    // ── QUÍMICA BXM ──────────────────────────────────────────────────────────
    val QUIMICA_BXM_UNITS = listOf(
        CourseUnitData(
            id = "cr_quim_bxm_u01",
            track = CurriculumTrack.QUIMICA_BXM,
            unitNumber = 1,
            targetMonth = 4,
            monthName = "Abril",
            title = "Estequiometría y Reacciones Químicas",
            description = "Masa molar, mol, balanceo por tanteo y leyes ponderales.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_quim_bxm_c_estequiometria",
                    conceptCode = "CR_QUIM_BXM_ESTEQUIOMETRIA",
                    title = "Cálculo Estequiométrico en Moles y Gramos",
                    description = "Conversión mol-gramo y relaciones estequiométricas.",
                    targetMonth = 4,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_quim_bxm_masa_molar",
                            conceptId = "cr_quim_bxm_c_estequiometria",
                            title = "Masa Molar del Agua (H₂O)",
                            prompt = "Teniendo H = 1 g/mol y O = 16 g/mol, ¿cuál es la masa molar de 1 mol de agua (H₂O)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("18 g/mol (2 × 1 + 16)", "17 g/mol", "32 g/mol", "8 g/mol"),
                            correctOptionIndex = 0,
                            explanation = "H₂O = (2 × 1.0) + 16.0 = 18 g/mol."
                        )
                    )
                )
            )
        )
    )

    // ── ESTUDIOS SOCIALES BXM ────────────────────────────────────────────────
    val SOCIALES_BXM_UNITS = listOf(
        CourseUnitData(
            id = "cr_soc_bxm_u01",
            track = CurriculumTrack.SOCIALES_BXM,
            unitNumber = 1,
            targetMonth = 4,
            monthName = "Abril",
            title = "Costa Rica en el Siglo XX: Reformas Sociales y 1948",
            description = "Garantías Sociales, Universidad de Costa Rica, Código de Trabajo y Constitución de 1949.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc_bxm_c_cr_siglo_xx",
                    conceptCode = "CR_SOC_BXM_CR_SIGLO_XX",
                    title = "Las Reformas Sociales de los Años 40",
                    description = "Creación de la CCSS, UCR y garantías laborales en Costa Rica.",
                    targetMonth = 4,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc_bxm_reformas_40",
                            conceptId = "cr_soc_bxm_c_cr_siglo_xx",
                            title = "El Capítulo de Garantías Sociales",
                            prompt = "¿Bajo cuál administración presidencial se promulgaron las Reformas Sociales de los años cuarenta en Costa Rica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Dr. Rafael Ángel Calderón Guardia",
                                "José Figueres Ferrer",
                                "Cleto González Víquez",
                                "Ricardo Jiménez Oreamuno"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El Dr. Rafael Ángel Calderón Guardia impulsó la creación de la CCSS, la UCR y el Código de Trabajo."
                        )
                    )
                )
            )
        )
    )

    // ── EDUCACIÓN CÍVICA BXM ─────────────────────────────────────────────────
    val CIVICA_BXM_UNITS = listOf(
        CourseUnitData(
            id = "cr_civ_bxm_u01",
            track = CurriculumTrack.CIVICA_BXM,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Régimen Democrático Costarricense y Poderes",
            description = "División de poderes, Tribunal Supremo de Elecciones y leyes de inclusión (Ley 7600).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ_bxm_c_democracia",
                    conceptCode = "CR_CIV_BXM_DEMOCRACIA",
                    title = "Institucionalidad Democrática y Control Político",
                    description = "Funciones de los poderes Legislativo, Ejecutivo, Judicial y el TSE.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ_bxm_tse",
                            conceptId = "cr_civ_bxm_c_democracia",
                            title = "El Rango Constitucional del TSE",
                            prompt = "¿Cuál es la función exclusiva del Tribunal Supremo de Elecciones (TSE) en Costa Rica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Organizar, dirigir y vigilar de forma independiente los actos relativos al sufragio",
                                "Aprobar las leyes de la República",
                                "Nombrar a los ministros de gobierno",
                                "Juzgar delitos penales comunes"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El TSE tiene rango constitucional independiente para garantizar elecciones libres y transparentes."
                        )
                    )
                )
            )
        )
    )

    // ── INGLÉS BXM ───────────────────────────────────────────────────────────
    val INGLES_BXM_UNITS = listOf(
        CourseUnitData(
            id = "cr_ing_bxm_u01",
            track = CurriculumTrack.INGLES_BXM,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Reading Comprehension: Science, Technology & Environment",
            description = "Estrategias de lectura en inglés B1/B2, skimming, scanning y vocabulario técnico.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing_bxm_c_reading",
                    conceptCode = "CR_ING_BXM_READING",
                    title = "Reading Strategies in Technical Contexts",
                    description = "Comprensión de hechos principales, inferencia y vocabulario en inglés.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing_bxm_scanning",
                            conceptId = "cr_ing_bxm_c_reading",
                            title = "Electric Vehicles Text Scanning",
                            prompt = "Text: 'Electric vehicles produce zero tailpipe emissions, significantly reducing urban air pollution in major cities.' What is the main environmental benefit mentioned?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "They produce zero tailpipe emissions and reduce urban air pollution.",
                                "They are faster than diesel trucks.",
                                "They require frequent oil changes.",
                                "They have louder exhaust systems."
                            ),
                            correctOptionIndex = 0,
                            explanation = "The sentence explicitly states that zero tailpipe emissions reduce urban air pollution."
                        )
                    )
                )
            )
        )
    )

    // ── FÍSICA BXM (10.º - 11.º / DGEC) ──────────────────────────────────────
    val FISICA_BXM_UNITS = listOf(
        CourseUnitData(
            id = "cr_fis_bxm_u01",
            track = CurriculumTrack.FISICA_BXM,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Cinemática y Dinámica Clásica Newtoniana",
            description = "Vectores, leyes del movimiento de Newton, fuerza neta, masa inercial y fricción.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_fis_bxm_c_newton",
                    conceptCode = "CR_FIS_BXM_NEWTON",
                    title = "Leyes del Movimiento de Newton y Fricción",
                    description = "Segunda ley (F = m*a), fuerza de rozamiento estática y dinámica.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_fis_bxm_fuerza_neta",
                            conceptId = "cr_fis_bxm_c_newton",
                            title = "Cálculo de Desaceleración en Frenado",
                            prompt = "Un vehículo de masa m = 1200 kg frena aplicando una fuerza neta constante de fricción de 6000 N. ¿Cuál es su aceleración (desaceleración)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "-5 m/s² en sentido contrario al movimiento",
                                "-0.2 m/s²",
                                "7200 m/s²",
                                "-2 m/s²"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Por la 2da Ley de Newton (a = F/m): a = -6000 N / 1200 kg = -5 m/s²."
                        ),
                        InteractiveTaskData(
                            id = "task_fis_bxm_transfer_friccion",
                            conceptId = "cr_fis_bxm_c_newton",
                            title = "Dinámica de Adherencia en Asfalto Mojado",
                            prompt = "Si el coeficiente de fricción neumático-asfalto cae de μ = 0.8 (seco) a μ = 0.4 (mojado), ¿qué ocurre con la distancia requerida de frenado a igual velocidad inicial?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Se duplica exactamente, porque la desaceleración máxima disponible se reduce a la mitad.",
                                "Se reduce a la mitad.",
                                "Permanece idéntica.",
                                "Se multiplica por cuatro."
                            ),
                            correctOptionIndex = 0,
                            explanation = "La distancia de frenado d = v²/(2*μ*g). Al ser inversamente proporcional a μ, si μ se divide por 2, la distancia de detención se duplica."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_fis_bxm_u02",
            track = CurriculumTrack.FISICA_BXM,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Termodinámica y Transferencia de Calor",
            description = "Primera ley de la termodinámica, calorimetría, conducción, convección y radiación.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_fis_bxm_c_termo",
                    conceptCode = "CR_FIS_BXM_TERMO",
                    title = "Balance Térmico e Intercambio de Calor",
                    description = "Q = m * c * ΔT y disipación térmica en sistemas cerrados y abiertos.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_fis_bxm_calor_latente",
                            conceptId = "cr_fis_bxm_c_termo",
                            title = "Transferencia de Calor por Convección",
                            prompt = "En el radiador de un motor, el líquido refrigerante caliente disipa calor hacia el aire forzado por el electroventilador. ¿Qué mecanismo de transferencia predomina?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Convección forzada líquido-sólido y sólido-aire con aletas de conducción de aluminio",
                                "Radiación electromagnética pura exclusivamente",
                                "Conducción en el vacío",
                                "Fisión térmica"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El calor pasa por conducción en las paredes de aluminio de los tubos y es extraído por convección forzada del aire."
                        )
                    )
                )
            )
        )
    )

    // ── 7.º AÑO: ESPAÑOL ─────────────────────────────────────────────────────
    val ESPANOL_7_UNITS = listOf(
        CourseUnitData(
            id = "cr_esp7_u01",
            track = CurriculumTrack.ESPANOL_7,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Géneros Literarios y Comprensión del Texto Expositivo",
            description = "Estructura del texto informativo, coherencia, cohesión y análisis de ideas principales.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp7_c_texto_exp",
                    conceptCode = "CR_ESP7_TEXTO_EXP",
                    title = "Estructura y Coherencia del Texto Informativo",
                    description = "Identificación de tesis, ideas de soporte y vocabulario contextual.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp7_idea_principal",
                            conceptId = "cr_esp7_c_texto_exp",
                            title = "Extracción de Idea Principal",
                            prompt = "En un manual técnico que detalla: 'Antes de desconectar la batería, asegúrese de apagar el switch para evitar picos de voltaje que dañen la ECU.' ¿Cuál es la idea central?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Prevenir sobretensiones apagando el interruptor previo a desconectar la fuente eléctrica.",
                                "Comprar una batería nueva.",
                                "La ECU no sufre daños por voltaje.",
                                "Desconectar la batería con el vehículo encendido."
                            ),
                            correctOptionIndex = 0,
                            explanation = "La instrucción central estipula apagar el switch como medida preventiva obligatoria ante picos de tensión."
                        )
                    )
                )
            )
        )
    )

    // ── 8.º AÑO: ESPAÑOL ─────────────────────────────────────────────────────
    val ESPANOL_8_UNITS = listOf(
        CourseUnitData(
            id = "cr_esp8_u01",
            track = CurriculumTrack.ESPANOL_8,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "La Narrativa Costarricense y la Oración Compuesta",
            description = "Oraciones coordinadas, subordinadas y conectores de causa y consecuencia.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp8_c_oracion_comp",
                    conceptCode = "CR_ESP8_ORACION_COMP",
                    title = "Sintaxis de Oraciones Coordinadas y Subordinadas",
                    description = "Uso de nexos sintácticos en la redacción formal.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp8_conector_causal",
                            conceptId = "cr_esp8_c_oracion_comp",
                            title = "Identificación de Conector Causal",
                            prompt = "En la frase: 'El motor falló puesto que la bomba de combustible perdió presión', ¿qué tipo de nexo es 'puesto que'?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Conector subordinante causal",
                                "Conector coordinante disyuntivo",
                                "Adverbio de tiempo",
                                "Preposición simple"
                            ),
                            correctOptionIndex = 0,
                            explanation = "'Puesto que' introduce la causa u origen del fallo mecánico."
                        )
                    )
                )
            )
        )
    )

    // ── 9.º AÑO: ESPAÑOL ─────────────────────────────────────────────────────
    val ESPANOL_9_UNITS = listOf(
        CourseUnitData(
            id = "cr_esp9_u01",
            track = CurriculumTrack.ESPANOL_9,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "El Ensayo Crítico y la Argumentación Formal",
            description = "Tesis, premisas lógicas, contraargumentos y vicios del lenguaje.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp9_c_ensayo",
                    conceptCode = "CR_ESP9_ENSAYO",
                    title = "Estructura Argumentativa y Análisis Crítico",
                    description = "Construcción de argumentos válidos y detección de falacias.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp9_tesis_argumento",
                            conceptId = "cr_esp9_c_ensayo",
                            title = "Justificación Técnica en Informe Pericial",
                            prompt = "¿Qué elemento convierte una afirmación en un argumento técnico sólido en un reporte pericial?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El respaldo empírico con mediciones, tolerancias del fabricante y evidencia física verificable.",
                                "La opinión personal sin mediciones.",
                                "El uso de adjetivos emotivos.",
                                "La longitud en páginas del documento."
                            ),
                            correctOptionIndex = 0,
                            explanation = "Un argumento pericial sólido se fundamenta en evidencia empírica cuantitativa y normas técnicas contrastables."
                        )
                    )
                )
            )
        )
    )

    // ── 7.º AÑO: ESTUDIOS SOCIALES ───────────────────────────────────────────
    val ESTUDIOS_SOCIALES_7_UNITS = listOf(
        CourseUnitData(
            id = "cr_soc7_u01",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_7,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Geografía Física y Gestión del Riesgo en Costa Rica",
            description = "Cordilleras, valles, cuencas hidrográficas y vulnerabilidad ante eventos naturales.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc7_c_geografia_cr",
                    conceptCode = "CR_SOC7_GEOGRAFIA_CR",
                    title = "Relieve, Clima y Cuencas Hidrográficas Nacionales",
                    description = "Estructura orográfica de Costa Rica y su impacto socioeconómico.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc7_cordilleras",
                            conceptId = "cr_soc7_c_geografia_cr",
                            title = "Eje Montañoso Central",
                            prompt = "¿Cuáles son las cordilleras volcánicas que conforman el eje central de Costa Rica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Guanacaste, Tilarán, Volcánica Central y Talamanca",
                                "Himalaya, Andes y Rocosas",
                                "Sierra Nevada y Escandinava",
                                "Cordillera de la Muerte y Alpes"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El sistema montañoso costarricense se subdivide en Guanacaste, Tilarán, Volcánica Central y Talamanca."
                        )
                    )
                )
            )
        )
    )

    // ── 8.º AÑO: ESTUDIOS SOCIALES ───────────────────────────────────────────
    val ESTUDIOS_SOCIALES_8_UNITS = listOf(
        CourseUnitData(
            id = "cr_soc8_u01",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_8,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Sociedades Precolombinas y Régimen Colonial",
            description = "Culturas autóctonas de Costa Rica (Diquís, Nicoya) y la colonización española.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc8_c_colonial",
                    conceptCode = "CR_SOC8_COLONIAL",
                    title = "Organización Social y Económica en la Costa Rica Colonial",
                    description = "Mestizaje, encomiendas, economía de subsistencia en el Valle Central.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc8_valle_central",
                            conceptId = "cr_soc8_c_colonial",
                            title = "Características Coloniales de Cartago",
                            prompt = "¿Cuál era la principal característica económica de la provincia de Costa Rica durante gran parte de la colonia?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Una provincia aislada con economía agrícola de subsistencia y escasa minería",
                                "Un emporio minero de oro y plata a gran escala",
                                "Un puerto marítimo industrial con astilleros mundiales",
                                "Una monarquía feudal independiente"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Costa Rica fue la provincia más austral y pobre de la Capitanía General de Guatemala, basada en agricultura de subsistencia."
                        )
                    )
                )
            )
        )
    )

    // ── 9.º AÑO: ESTUDIOS SOCIALES ───────────────────────────────────────────
    val ESTUDIOS_SOCIALES_9_UNITS = listOf(
        CourseUnitData(
            id = "cr_soc9_u01",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_9,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Costa Rica en el Siglo XX: Del Estado Liberal a las Reformas Sociales",
            description = "Crisis de los años 30, reformas sociales de 1943 (CCSS, UCR, Código de Trabajo) y Guerra Civil de 1948.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc9_c_reformas43",
                    conceptCode = "CR_SOC9_REFORMAS43",
                    title = "Reformas Sociales de los Años 40 y el Estado Benefactor",
                    description = "Alianza histórica y creación de la institucionalidad social costarricense.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc9_alianza43",
                            conceptId = "cr_soc9_c_reformas43",
                            title = "Pilares de las Garantías Sociales de 1943",
                            prompt = "¿Quiénes lideraron la alianza social que promulgó las Garantías Sociales y el Código de Trabajo en 1943?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Rafael Ángel Calderón Guardia, Manuel Mora Valverde y Monseñor Víctor Sanabria Martínez",
                                "Juan Rafael Mora Porras y William Walker",
                                "José María Castro Madriz y Braulio Carrillo",
                                "Tomás Guardia y Bernardo Soto"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La alianza entre el gobierno de Calderón Guardia, el Partido Vanguardia Popular (Manuel Mora) y la Iglesia Católica (Monseñor Sanabria) forjó las reformas sociales de 1943."
                        )
                    )
                )
            )
        )
    )

    // ── 7.º AÑO: EDUCACIÓN CÍVICA ────────────────────────────────────────────
    val CIVICA_7_UNITS = listOf(
        CourseUnitData(
            id = "cr_civ7_u01",
            track = CurriculumTrack.CIVICA_7,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Seguridad Vial y Responsabilidad Ciudadana en el Espacio Público",
            description = "Ley de Tránsito 9078, prevención de accidentes, señalización y convivencia vial.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ7_c_seguridad_vial",
                    conceptCode = "CR_CIV7_SEGURIDAD_VIAL",
                    title = "Seguridad Vial como Deber y Derecho Ciudadano",
                    description = "Factores humano, vehicular y ambiental en la prevención de siniestros viales.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ7_mantenimiento_preventivo",
                            conceptId = "cr_civ7_c_seguridad_vial",
                            title = "Inspección de Seguridad del Vehículo",
                            prompt = "Bajo el principio de responsabilidad civil ciudadana, ¿por qué es obligatorio mantener frenos, llantas y luces en perfecto estado?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Para proteger la vida propia y la de terceros en las vías públicas compartidas.",
                                "Únicamente para evitar multas económicas.",
                                "Para que el auto se vea más limpio.",
                                "No es obligatorio si no se viaja de noche."
                            ),
                            correctOptionIndex = 0,
                            explanation = "La seguridad vial es un imperativo ético y legal de protección colectiva del derecho a la vida."
                        )
                    )
                )
            )
        )
    )

    // ── 8.º AÑO: EDUCACIÓN CÍVICA ────────────────────────────────────────────
    val CIVICA_8_UNITS = listOf(
        CourseUnitData(
            id = "cr_civ8_u01",
            track = CurriculumTrack.CIVICA_8,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Derechos Humanos, Equidad y No Discriminación",
            description = "Declaración Universal de Derechos Humanos, igualdad de género, inclusión y Ley 7600.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ8_c_derechos_hum",
                    conceptCode = "CR_CIV8_DERECHOS_HUM",
                    title = "Derechos Humanos Fundamentales e Inclusión Social",
                    description = "Universalidad, indivisibilidad y garantías de accesibilidad para todas las personas.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ8_ley7600",
                            conceptId = "cr_civ8_c_derechos_hum",
                            title = "Accesibilidad en Comercios y Talleres",
                            prompt = "Según la Ley 7600 de Igualdad de Oportunidades, ¿qué obligación tienen los establecimientos abiertos al público?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Garantizar accesibilidad física universal sin barreras arquitectónicas.",
                                "Cobrar tarifas diferenciadas a personas con discapacidad.",
                                "Exigir permisos especiales para ingresar.",
                                "Atender únicamente por cita telefónica."
                            ),
                            correctOptionIndex = 0,
                            explanation = "La Ley 7600 exige accesibilidad física sin barreras en todos los locales de atención al público."
                        )
                    )
                )
            )
        )
    )

    // ── 9.º AÑO: EDUCACIÓN CÍVICA ────────────────────────────────────────────
    val CIVICA_9_UNITS = listOf(
        CourseUnitData(
            id = "cr_civ9_u01",
            track = CurriculumTrack.CIVICA_9,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "El Régimen Municipal y la Participación Cantonal",
            description = "Código Municipal, funciones de alcaldes, regidores, síndicos y rendición de cuentas cantonal.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ9_c_municipal",
                    conceptCode = "CR_CIV9_MUNICIPAL",
                    title = "Organización Municipal y Autonomía de los Gobiernos Locales",
                    description = "Competencias de los cantones en ordenamiento territorial, patentes y servicios comunitarios.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ9_patente_taller",
                            conceptId = "cr_civ9_c_municipal",
                            title = "Permisos Comerciales y Regulación Cantonal",
                            prompt = "¿Qué entidad pública otorga la licencia comercial (patente) para operar un taller o negocio en un cantón?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "La Municipalidad del respectivo cantón",
                                "La Asamblea Legislativa directamente",
                                "La Fuerza Pública local",
                                "El Ministerio de Hacienda exclusivamente"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Las municipalidades poseen potestad constitucional y legal para regular y otorgar licencias comerciales cantonales."
                        )
                    )
                )
            )
        )
    )

    // ── 7.º AÑO: INGLÉS ──────────────────────────────────────────────────────
    val INGLES_7_UNITS = listOf(
        CourseUnitData(
            id = "cr_ing7_u01",
            track = CurriculumTrack.INGLES_7,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Personal Identity, School Life & Daily Routines",
            description = "Present simple tense, daily activities, basic questions and classroom vocabulary (A1+).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing7_c_routines",
                    conceptCode = "CR_ING7_ROUTINES",
                    title = "Daily Routines and Time Expressions",
                    description = "Describing habits, schedules, and safety instructions using Present Simple.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing7_routine_order",
                            conceptId = "cr_ing7_c_routines",
                            title = "Workshop Safety Routine Expression",
                            prompt = "Complete the sentence: 'A responsible apprentice always _______ safety goggles before operating machinery.'",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "wears",
                                "wearing",
                                "wore",
                                "wear"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Third-person singular 'apprentice' requires 'wears' in the Present Simple tense."
                        )
                    )
                )
            )
        )
    )

    // ── 8.º AÑO: INGLÉS ──────────────────────────────────────────────────────
    val INGLES_8_UNITS = listOf(
        CourseUnitData(
            id = "cr_ing8_u01",
            track = CurriculumTrack.INGLES_8,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Travel, Environment & Community Interactions",
            description = "Past simple vs past continuous, giving directions, eco-tourism and national parks (A2).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing8_c_environment",
                    conceptCode = "CR_ING8_ENVIRONMENT",
                    title = "Environmental Actions and Past Experiences",
                    description = "Expressing past actions, conservation policies and technical directions.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing8_directions",
                            conceptId = "cr_ing8_c_environment",
                            title = "Giving Mechanical Assistance in English",
                            prompt = "A tourist asks: 'Excuse me, where can I check my tire pressure?' Which response is correct?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "'Go straight for 200 meters; the service station has an air pump on the right.'",
                                "'Yesterday I was checking my phone.'",
                                "'Tires are made of rubber.'",
                                "'I like driving fast.'"
                            ),
                            correctOptionIndex = 0,
                            explanation = "The answer provides clear, polite imperative directions to the nearest service point."
                        )
                    )
                )
            )
        )
    )

    // ── 9.º AÑO: INGLÉS ──────────────────────────────────────────────────────
    val INGLES_9_UNITS = listOf(
        CourseUnitData(
            id = "cr_ing9_u01",
            track = CurriculumTrack.INGLES_9,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Science, Technology, Careers & Workplace Communication",
            description = "Modal verbs (must, should, have to), future plans, interpreting technical alerts and datasheets (B1).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing9_c_workplace",
                    conceptCode = "CR_ING9_WORKPLACE",
                    title = "Technical Alerts and Modal Verbs in Engineering",
                    description = "Understanding imperative manuals and technical safety standards.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing9_modal_safety",
                            conceptId = "cr_ing9_c_workplace",
                            title = "Interpreting Warning Labels",
                            prompt = "Warning label: 'High Voltage! Technicians must isolate power supply prior to servicing.' What is mandatory?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Disconnecting electrical power before performing maintenance.",
                                "Leaving the circuit live during inspection.",
                                "Replacing wires without gloves.",
                                "Ignoring the indicator light."
                            ),
                            correctOptionIndex = 0,
                            explanation = "'Must isolate power supply' establishes an absolute safety obligation before starting service."
                        )
                    )
                )
            )
        )
    )

    // ── 7.º AÑO: CIENCIAS ────────────────────────────────────────────────────
    val CIENCIAS_7_UNITS = listOf(
        CourseUnitData(
            id = "cr_cie7_u01",
            track = CurriculumTrack.CIENCIAS_7,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Materia, Sustancias Puras, Mezclas y Métodos de Separación",
            description = "Propiedades físicas y químicas, elementos, compuestos, mezclas homogéneas y heterogéneas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cie7_c_materia",
                    conceptCode = "CR_CIE7_MATERIA",
                    title = "Clasificación de la Materia y Procesos de Separación",
                    description = "Filtración, decantación, destilación y cambios físicos vs químicos.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cie7_separacion_mezclas",
                            conceptId = "cr_cie7_c_materia",
                            title = "Filtrado y Decantación de Combustible",
                            prompt = "En un motor diésel con trampa de agua en el filtro, ¿qué propiedad física permite que el agua se separe del combustible y se asiente en el fondo?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "La mayor densidad del agua respecto al diésel e inmiscibilidad entre ambos líquidos (decantación)",
                                "La solubilidad total del agua en el hidrocarburo",
                                "La evaporación instantánea del agua a temperatura ambiente",
                                "La atracción magnética"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El agua es más densa que el diésel (~1.0 g/cm³ vs ~0.84 g/cm³) y no se disuelve, sedimentando por gravedad en la trampa decantadora."
                        )
                    )
                )
            )
        )
    )

    // ── 8.º AÑO: CIENCIAS ────────────────────────────────────────────────────
    val CIENCIAS_8_UNITS = listOf(
        CourseUnitData(
            id = "cr_cie8_u01",
            track = CurriculumTrack.CIENCIAS_8,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "La Célula y Fisiología de los Sistemas del Cuerpo Humano",
            description = "Organelas celulares, respiración aerobia, sistemas circulatorio, respiratorio y locomotor.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cie8_c_celula",
                    conceptCode = "CR_CIE8_CELULA",
                    title = "Respiración Celular y Toxicología Ocupacional",
                    description = "Mitocondrias, ATP e impacto de gases tóxicos en la fisiología humana.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cie8_toxicologia_co",
                            conceptId = "cr_cie8_c_celula",
                            title = "Peligro del Monóxido de Carbono (CO)",
                            prompt = "¿Por qué es mortal operar un motor de combustión en un taller cerrado sin extractor de gases?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El monóxido de carbono (CO) se une irreversiblemente a la hemoglobina, bloqueando el transporte de oxígeno a las células.",
                                "El motor consume todo el nitrógeno del aire.",
                                "El vapor de agua generado destruye los glóbulos blancos.",
                                "El dióxido de carbono congela los pulmones."
                            ),
                            correctOptionIndex = 0,
                            explanation = "El CO tiene una afinidad ~200 veces mayor que el O₂ por la hemoglobina (formando carboxihemoglobina), provocando hipoxia y asfixia celular celular rápida."
                        )
                    )
                )
            )
        )
    )

    // ── 9.º AÑO: CIENCIAS ────────────────────────────────────────────────────
    val CIENCIAS_9_UNITS = listOf(
        CourseUnitData(
            id = "cr_cie9_u01",
            track = CurriculumTrack.CIENCIAS_9,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "El Átomo, Tabla Periódica, Reacciones Químicas y Genética",
            description = "Estructura atómica, enlaces, electronegatividad, leyes de la herencia y reacciones redox.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cie9_c_atomo",
                    conceptCode = "CR_CIE9_ATOMO",
                    title = "Estructura Atómica y Reacciones de Óxido-Reducción",
                    description = "Electrones de valencia, flujo de corriente y electroquímica de baterías.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cie9_bateria_redox",
                            conceptId = "cr_cie9_c_atomo",
                            title = "Electroquímica en Baterías de Plomo-Ácido",
                            prompt = "En la batería de un vehículo (Pb + PbO₂ + 2H₂SO₄ ⇌ 2PbSO₄ + 2H₂O), ¿qué fenómeno químico produce la corriente de 12V?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Una reacción de óxido-reducción que genera transferencia espontánea de electrones entre los electrodos.",
                                "Una fricción mecánica interna entre las placas.",
                                "La evaporación de ácido sulfúrico hacia los bornes.",
                                "Una fisión nuclear del núcleo de plomo."
                            ),
                            correctOptionIndex = 0,
                            explanation = "La reacción redox convierte energía química en energía eléctrica mediante transferencia de electrones entre el ánodo de plomo y el cátodo de óxido de plomo."
                        )
                    )
                )
            )
        )
    )

    // ── ESTUDIOS SOCIALES PRIMARIA ───────────────────────────────────────────
    val ESTUDIOS_SOCIALES_PRIMARIA_UNITS = listOf(
        CourseUnitData(
            id = "cr_soc_pri_u01",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_PRIMARIA,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Mi Comunidad, Costa Rica y sus Símbolos Patrios",
            description = "Puntos cardinales, paisaje geográfico, provincias de Costa Rica y emblemas nacionales.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc_pri_c_comunidad",
                    conceptCode = "CR_SOC_PRI_COMUNIDAD",
                    title = "Identidad Cantonal y Símbolos de Costa Rica",
                    description = "Orientación espacial, respeto comunitario e historia patria elemental.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc_pri_simbolos",
                            conceptId = "cr_soc_pri_c_comunidad",
                            title = "Símbolos Nacionales de Costa Rica",
                            prompt = "¿Cuál es el ave nacional de Costa Rica reconocida por su canto al inicio de las lluvias?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "El Yigüirro (Turdus grayi)",
                                "La Lapa Roja",
                                "El Tucán pico iris",
                                "El Colibrí"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El Yigüirro fue declarado Ave Nacional de Costa Rica en 1977 como tributo a su canto anunciador de la temporada de lluvias."
                        )
                    )
                )
            )
        )
    )

    // ── INGLÉS PRIMARIA ──────────────────────────────────────────────────────
    val INGLES_PRIMARIA_UNITS = listOf(
        CourseUnitData(
            id = "cr_ing_pri_u01",
            track = CurriculumTrack.INGLES_PRIMARIA,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Welcome: Colors, Numbers, Family & Basic Commands",
            description = "Greetings, primary colors, numbers 1 to 20, everyday school items (Pre-A1).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing_pri_c_basics",
                    conceptCode = "CR_ING_PRI_BASICS",
                    title = "Everyday Vocabulary and Polite Expressions",
                    description = "Fundamental words, polite greetings, color recognition and counting.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing_pri_colors_signs",
                            conceptId = "cr_ing_pri_c_basics",
                            title = "Safety Color Recognition",
                            prompt = "What color is standard on emergency stop buttons worldwide?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Red (Rojo)",
                                "Green (Verde)",
                                "Blue (Azul)",
                                "Yellow (Amarillo)"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Red is universally used for STOP and danger alerts."
                        )
                    )
                )
            )
        )
    )
}
