package com.elysium369.meet.education.data

import com.elysium369.meet.education.domain.EpistemicTruthState

/**
 * Catálogo Curricular Completo para Educación Diversificada y Bachillerato por Madurez (BxM 10.º - 11.º) — MEP Costa Rica 2026.
 * Incluye Matemática BxM, Español BxM, Biología BxM, Química BxM, Física BxM, Estudios Sociales BxM, Cívica BxM e Inglés BxM (C1 Mastery).
 * Cada materia incluye múltiples unidades estructuradas según el temario oficial de la DGEC / MEP con tareas de transferencia PISA Nivel 6.
 */
object DiversifiedCurriculumSeed {

    // ══════════════════════════════════════════════════════════════════════════
    // MATEMÁTICA BACHILLERATO POR MADUREZ (10.º - 11.º / DGEC)
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_mat_bxm_transfer_posicion_recta",
                            conceptId = "cr_mat_bxm_c_circunferencia",
                            title = "Posición Relativa de Recta Tangente a Engranaje",
                            prompt = "Una recta y = mx + b toca a una rueda de radio r en exactamente un punto. Al sustituir la recta en la circunferencia, ¿cuál es el valor del discriminante Δ?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Δ = 0 (la recta es tangente, intersecta en un único punto)",
                                "Δ > 0 (secante, dos puntos)",
                                "Δ < 0 (exterior, ningún punto)",
                                "Δ = -1"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La condición geométrica y analítica de tangencia exige que la ecuación cuadrática resultante tenga discriminante nulo (Δ = 0)."
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
                        ),
                        InteractiveTaskData(
                            id = "task_mat_bxm_log_propiedad",
                            conceptId = "cr_mat_bxm_c_funciones",
                            title = "Propiedad Fundamental de Logaritmos",
                            prompt = "¿A qué equivale la expresión log_b(x × y) según las leyes de logaritmos?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("log_b(x) + log_b(y)", "log_b(x) × log_b(y)", "log_b(x) - log_b(y)", "log_b(x) ÷ log_b(y)"),
                            correctOptionIndex = 0,
                            explanation = "El logaritmo de un producto es la suma de los logaritmos de los factores: log_b(xy) = log_b(x) + log_b(y)."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat_bxm_u03",
            track = CurriculumTrack.MATEMATICA_BXM,
            unitNumber = 3,
            targetMonth = 7,
            monthName = "Julio",
            title = "Geometría del Espacio y Polígonos Regulares",
            description = "Apotema, radio, perímetro, área de polígonos regulares y volumen de prismas, pirámides y cilindros.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat_bxm_c_poligonos_espacio",
                    conceptCode = "CR_MAT_BXM_POLIGONOS_ESPACIO",
                    title = "Polígonos Regulares y Cuerpos en el Espacio",
                    description = "Fórmulas de área poligonal A = (P × a)/2 y cálculo de volúmenes espaciales.",
                    targetMonth = 7,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat_bxm_area_hexagono",
                            conceptId = "cr_mat_bxm_c_poligonos_espacio",
                            title = "Área de un Hexágono Regular",
                            prompt = "Un hexágono regular tiene perímetro P = 60 cm y apotema a = 5√3 cm. ¿Cuál es su área exacta?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "150√3 cm² (Área = (P × a) / 2 = (60 × 5√3) / 2 = 150√3)",
                                "300√3 cm²",
                                "100√3 cm²",
                                "75√3 cm²"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Área = (Perímetro × apotema)/2 = (60 × 5√3)/2 = 300√3 / 2 = 150√3 cm²."
                        ),
                        InteractiveTaskData(
                            id = "task_mat_bxm_transfer_volumen_cilindro_motor",
                            conceptId = "cr_mat_bxm_c_poligonos_espacio",
                            title = "Cálculo de Cilindrada Unitaria de un Motor",
                            prompt = "Un cilindro de motor tiene diámetro D = 80 mm (radio r = 40 mm = 4 cm) y carrera h = 10 cm. Usando V = π × r² × h, ¿cuál es su cilindrada unitaria en cm³ (cc)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "502.65 cc (V = π × 4² × 10 = 160π ≈ 502.65 cm³)",
                                "160 cc",
                                "1256 cc",
                                "800 cc"
                            ),
                            correctOptionIndex = 0,
                            explanation = "V = π × (4 cm)² × 10 cm = π × 16 × 10 = 160π cm³ ≈ 502.65 cc unitarios."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat_bxm_u04",
            track = CurriculumTrack.MATEMATICA_BXM,
            unitNumber = 4,
            targetMonth = 9,
            monthName = "Septiembre",
            title = "Estadística Inferencial y Posición Relativa",
            description = "Varianza (σ²), desviación estándar (σ), coeficiente de variación (CV) y puntuación estandarizada (Z).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat_bxm_c_posicion_relativa",
                    conceptCode = "CR_MAT_BXM_POSICION_RELATIVA",
                    title = "Estandarización y Coeficiente de Variación",
                    description = "Fórmula Z = (x - μ) / σ para comparar variables con diferentes medias y unidades.",
                    targetMonth = 9,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat_bxm_coeficiente_variacion",
                            conceptId = "cr_mat_bxm_c_posicion_relativa",
                            title = "Cálculo del Coeficiente de Variación (CV)",
                            prompt = "Un lote de producción tiene media μ = 50 mm y desviación estándar σ = 2 mm. ¿Cuál es su CV porcentual?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("4% (CV = (σ / μ) × 100% = (2 / 50) × 100%)", "25%", "2%", "0.04%"),
                            correctOptionIndex = 0,
                            explanation = "CV = (σ / μ) × 100 = (2 / 50) × 100 = 4% de variabilidad relativa."
                        ),
                        InteractiveTaskData(
                            id = "task_mat_bxm_transfer_posicion_relativa_z",
                            conceptId = "cr_mat_bxm_c_posicion_relativa",
                            title = "Puntuación Z en Comparación de Desempeño Mecánico",
                            prompt = "En la prueba A, un motor rinde x₁ = 90 con μ = 80 y σ = 5 (Z₁ = (90-80)/5 = +2.0). En la prueba B, otro rinde x₂ = 110 con μ = 100 y σ = 10 (Z₂ = (110-100)/10 = +1.0). ¿Cuál motor tuvo mejor posición relativa de desempeño?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El motor de la prueba A, porque su puntuación estandarizada Z₁ = +2.0 está a 2 desviaciones sobre la media frente a solo +1.0 del segundo",
                                "El motor B porque 110 es mayor que 90",
                                "Ambos tuvieron exactamente el mismo rendimiento",
                                "No se pueden comparar"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La puntuación Z permite comparar posiciones relativas justas independientemente de la magnitud de la escala."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // ESPAÑOL BACHILLERATO POR MADUREZ (10.º - 11.º / DGEC)
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_esp_bxm_transfer_figuras_retoricas",
                            conceptId = "cr_esp_bxm_c_analisis_literario",
                            title = "Metáfora vs Símil en Textos Canónicos del MEP",
                            prompt = "En la frase 'El motor era un león furioso rugiendo en la cuesta', ¿qué figura literaria predomina y por qué?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Metáfora pura (identifica directamente el motor con el león sin usar nexo comparativo 'como')",
                                "Símil o comparación con nexo",
                                "Hipérbole de disminución",
                                "Prosopopeya inversa"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Al omitir el conector comparativo ('como', 'cual'), la transposición conceptual es una metáfora directa."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp_bxm_u02",
            track = CurriculumTrack.ESPANOL_BXM,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Poesía y Compromiso Social: De Jorge Debravo a Eunice Odio",
            description = "Lírica costarricense del siglo XX, métrica, rima, verso libre y poesía de denuncia social.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp_bxm_c_poesia_social",
                    conceptCode = "CR_ESP_BXM_POESIA_SOCIAL",
                    title = "La Poesía Humanista de Jorge Debravo",
                    description = "Poesía como herramienta de fraternidad humana, justicia y redención social.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp_bxm_debravo_tematica",
                            conceptId = "cr_esp_bxm_c_poesia_social",
                            title = "Temática Central en la Obra de Jorge Debravo",
                            prompt = "En poemarios como 'Nosotros los hombres', ¿cuál es el postulado ético y poético de Jorge Debravo?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "La solidaridad militante con los desposeídos, el valor redentor del amor y el trabajo fraterno",
                                "El desprecio a la humanidad y la soledad absoluta",
                                "La alabanza de la aristocracia europea",
                                "La mitología griega antigua"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Debravo erigió una poesía de raíz popular, profundamente humanista y comprometida con el dolor social."
                        ),
                        InteractiveTaskData(
                            id = "task_esp_bxm_transfer_eunice_odio_vanguardia",
                            conceptId = "cr_esp_bxm_c_poesia_social",
                            title = "La Trascendencia Cosmopolita de Eunice Odio",
                            prompt = "En la obra 'El tránsito de fuego' de la poeta costarricense Eunice Odio, ¿qué rasgo estético revolucionó las letras hispanoamericanas?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Una poesía metafísica, mística y cósmica de profunda densidad simbólica que rompió con el costumbrismo tradicional",
                                "Rimas infantiles simples",
                                "Crónicas de partidos de fútbol",
                                "Poemas en prosa dedicados al café"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Eunice Odio es reconocida universalmente como una de las voces líricas y metafísicas más deslumbrantes de América."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp_bxm_u03",
            track = CurriculumTrack.ESPANOL_BXM,
            unitNumber = 3,
            targetMonth = 7,
            monthName = "Julio",
            title = "Sintaxis Compuesta: Proposiciones Coordinadas y Subordinadas",
            description = "Subordinadas sustantivas (función de sujeto y CD), adjetivas y adverbiales (tiempo, modo, causa, condición).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp_bxm_c_sintaxis_compuesta",
                    conceptCode = "CR_ESP_BXM_SINTAXIS_COMPUESTA",
                    title = "Clasificación Rigurosa de Proposiciones Subordinadas",
                    description = "Análisis arbóreo y funcional de proposiciones en el examen de Bachillerato MEP.",
                    targetMonth = 7,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp_bxm_subordinada_sustantiva_cd",
                            conceptId = "cr_esp_bxm_c_sintaxis_compuesta",
                            title = "Subordinada Sustantiva en Función de Objeto Directo",
                            prompt = "En la oración: 'El informe certificó que los frenos estaban desgastados', ¿qué función sintáctica cumple la proposición subordinada?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Complemento Directo (El informe lo certificó)",
                                "Sujeto del verbo principal",
                                "Complemento Circunstancial de Modo",
                                "Atributo de verbo copulativo"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La proposición introducida por 'que' responde a ¿qué certificó? y se conmuta por el pronombre átono 'lo'."
                        ),
                        InteractiveTaskData(
                            id = "task_esp_bxm_transfer_oracion_condicional_peritaje",
                            conceptId = "cr_esp_bxm_c_sintaxis_compuesta",
                            title = "Redacción Jurídica sin Ambigüedad Condicional",
                            prompt = "Para dictaminar en un laudo arbitral automotriz, ¿cuál de las siguientes opciones expresa una relación causal-condicional con máxima precisión técnico-jurídica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "En caso de que se compruebe que la temperatura superó los 115 °C, la garantía quedará invalidada ipso facto debido a sobrecalentamiento no imputable a defecto de fábrica.",
                                "Si el carro calienta mucho entonces no le pagan nada.",
                                "Porque calentó y por eso la garantía no sirve.",
                                "El carro calentó por culpa del chofer quizás."
                            ),
                            correctOptionIndex = 0,
                            explanation = "La sintaxis jurídica exige conector condicional formal ('En caso de que...'), precisión empírica ('115 °C') y tipificación del nexo causal."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp_bxm_u04",
            track = CurriculumTrack.ESPANOL_BXM,
            unitNumber = 4,
            targetMonth = 9,
            monthName = "Septiembre",
            title = "Composición y Redacción del Ensayo Formal de Bachillerato",
            description = "Normas de composición de la DGEC (mínimo 300 palabras), coherencia, cohesión, variedad léxica y ortografía impecable.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp_bxm_c_ensayo_bachillerato",
                    conceptCode = "CR_ESP_BXM_ENSAYO_BACHILLERATO",
                    title = "Estructura Canónica del Ensayo del MEP",
                    description = "Párrafo introductorio con tesis explícita, párrafos de desarrollo argumentativo y párrafo de cierre concluyente.",
                    targetMonth = 9,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp_bxm_criterios_evaluacion_ensayo",
                            conceptId = "cr_esp_bxm_c_ensayo_bachillerato",
                            title = "Criterios de Calificación del Ensayo de Bachillerato",
                            prompt = "¿Cuáles son los tres aspectos medulares evaluados por la rúbrica oficial de la DGEC en la prueba de redacción?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Fondo (ideas, coherencia y estructura), Forma (riqueza léxica y variedad sintáctica) y Mecánica (ortografía, acentuación y puntuación)",
                                "La belleza de la letra manuscrita exclusivamente",
                                "La velocidad con la que se entrega la prueba",
                                "El tamaño de los márgenes en centímetros"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La rúbrica ministerial desglosa puntajes en fondo, forma lingüística y corrección ortográfica y mecánica."
                        ),
                        InteractiveTaskData(
                            id = "task_esp_bxm_transfer_conector_conclusion",
                            conceptId = "cr_esp_bxm_c_ensayo_bachillerato",
                            title = "Conectores Lógicos para el Párrafo de Conclusión",
                            prompt = "¿Cuál de los siguientes grupos de conectores textuales es el apropiado para iniciar el párrafo de conclusión en un ensayo formal?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "En conclusión, En síntesis, Por consiguiente, De los argumentos expuestos se colige que...",
                                "En primer lugar, Para comenzar, Érase una vez...",
                                "Pero, Sin embargo, Aunque, No obstante...",
                                "Por ejemplo, A saber, Como muestra..."
                            ),
                            correctOptionIndex = 0,
                            explanation = "Los conectores conclusivos o recapitulativos cierran el discurso sintetizando la tesis probada."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // BIOLOGÍA BACHILLERATO POR MADUREZ (10.º - 11.º / DGEC)
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_bio_bxm_homocigoto_recesivo",
                            conceptId = "cr_bio_bxm_c_genetica",
                            title = "Genotipo Homocigoto Recesivo",
                            prompt = "¿Cómo se denomina al individuo que posee dos alelos idénticos que solo se manifiestan en ausencia del alelo dominante (aa)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Homocigoto recesivo", "Heterocigoto dominante", "Codominante", "Homocigoto dominante"),
                            correctOptionIndex = 0,
                            explanation = "El estado 'aa' corresponde a homocigosis recesiva según la nomenclatura genética estándar."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_bio_bxm_u02",
            track = CurriculumTrack.BIOLOGIA_BXM,
            unitNumber = 2,
            targetMonth = 6,
            monthName = "Junio",
            title = "Bioenergética: Fotosíntesis vs Respiración Celular",
            description = "Cloroplastos, mitocondrias, fase luminosa/oscura, glucólisis, ciclo de Krebs y transporte de electrones.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_bio_bxm_c_bioenergetica",
                    conceptCode = "CR_BIO_BXM_BIOENERGETICA",
                    title = "Metabolismo Celular y Síntesis de ATP",
                    description = "Ecuaciones balanceadas de la fotosíntesis y la respiración celular aerobia.",
                    targetMonth = 6,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_bio_bxm_ecuacion_fotosintesis",
                            conceptId = "cr_bio_bxm_c_bioenergetica",
                            title = "Ecuación Global de la Fotosíntesis",
                            prompt = "¿Cuáles son los reactivos y productos principales del proceso fotosintético en vegetales?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "6CO₂ + 6H₂O + Luz solar -> C₆H₁₂O₆ (Glucosa) + 6O₂",
                                "C₆H₁₂O₆ + 6O₂ -> 6CO₂ + 6H₂O",
                                "ATP + CO₂ -> Hidrógeno líquido",
                                "Oxígeno puro -> Ácido sulfúrico"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Los cloroplastos capturan dióxido de carbono y agua con energía lumínica para sintetizar glucosa y liberar oxígeno."
                        ),
                        InteractiveTaskData(
                            id = "task_bio_bxm_transfer_fosforilacion_oxidativa",
                            conceptId = "cr_bio_bxm_c_bioenergetica",
                            title = "Rendimiento Energético de la Respiración Aerobia",
                            prompt = "Por cada molécula de glucosa completamente oxidada en la respiración celular aerobia, ¿cuántas moléculas netas de ATP se generan aproximadamente?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Entre 36 y 38 ATP (altísimo rendimiento en comparación con solo 2 ATP de la fermentación anaerobia)",
                                "Exactamente 2 ATP",
                                "1000 ATP",
                                "Cero ATP"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La cadena respiratoria mitocondrial y la ATP sintasa maximizan la producción a ~36-38 moles de ATP por mol de glucosa."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_bio_bxm_u03",
            track = CurriculumTrack.BIOLOGIA_BXM,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Evolución Biológica, Selección Natural y Especiación",
            description = "Teoría sintética de la evolución, adaptaciones anatómicas y fisiológicas, registro fósil y cladogramas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_bio_bxm_c_evolucion",
                    conceptCode = "CR_BIO_BXM_EVOLUCION",
                    title = "El Mecanismo de Selección Natural de Darwin",
                    description = "Variabilidad fenotípica, éxito reproductivo diferencial y adaptación ecológica.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_bio_bxm_seleccion_natural_definicion",
                            conceptId = "cr_bio_bxm_c_evolucion",
                            title = "Postulado Central de la Selección Natural",
                            prompt = "¿Qué postula la teoría de la evolución por selección natural desarrollada por Charles Darwin?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Los individuos con variaciones genéticas favorables sobreviven y se reproducen con mayor éxito, transmitiendo esos rasgos a las siguientes generaciones",
                                "Los organismos deciden conscientemente qué órganos modificar según sus deseos",
                                "Todos los animales evolucionan simultáneamente en la misma dirección",
                                "Las especies nunca cambian"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La presión ambiental selecciona positivamente los alelos que confieren ventajas adaptativas y reproductivas."
                        ),
                        InteractiveTaskData(
                            id = "task_bio_bxm_transfer_resistencia_bacteriana",
                            conceptId = "cr_bio_bxm_c_evolucion",
                            title = "Selección Natural en Tiempo Real: Resistencia a Antibióticos",
                            prompt = "¿Por qué el uso indebido e incompleto de antibióticos provoca la aparición de superbacterias multirresistentes?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El antibiótico elimina las bacterias sensibles pero deja con vida a las mutantes resistentes, las cuales se multiplican sin competencia",
                                "Las bacterias aprenden a comerse el medicamento",
                                "El medicamento pierde su fecha de caducidad en el estómago",
                                "Las bacterias se convierten en virus"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Es un ejemplo canónico de selección direccional artificial donde el fármaco actúa como agente selectivo implacable."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_bio_bxm_u04",
            track = CurriculumTrack.BIOLOGIA_BXM,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Ecología de Poblaciones, Ciclos Biogeoquímicos y Cambio Global",
            description = "Capacidad de carga (K), crecimiento logístico vs exponencial, ciclos del carbono y nitrógeno, y acidificación oceánica.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_bio_bxm_c_ecologia_global",
                    conceptCode = "CR_BIO_BXM_ECOLOGIA_GLOBAL",
                    title = "Dinámica de Ecosistemas y Ciclo del Carbono",
                    description = "Flujo de energía unidireccional y reciclaje biogeoquímico de materia.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_bio_bxm_ciclo_carbono_quema",
                            conceptId = "cr_bio_bxm_c_ecologia_global",
                            title = "Desbalance del Ciclo Global del Carbono",
                            prompt = "¿Por qué la quema acelerada de combustibles fósiles (carbón, petróleo, gas) altera el equilibrio del ciclo del carbono?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Libera a la atmósfera en décadas carbono que estuvo secuestrado bajo tierra durante cientos de millones de años, superando la capacidad de absorción de bosques y océanos",
                                "Elimina todo el oxígeno del planeta en un solo año",
                                "Enfría el núcleo de la Tierra",
                                "Convierte el agua del mar en gasolina"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La velocidad antrópica de liberación de CO₂ excede en órdenes de magnitud la tasa natural de sedimentación biológica."
                        ),
                        InteractiveTaskData(
                            id = "task_bio_bxm_transfer_capacidad_carga_k",
                            conceptId = "cr_bio_bxm_c_ecologia_global",
                            title = "Capacidad de Carga y Colapso Poblacional",
                            prompt = "En un ecosistema cerrado, si una población de herbívoros sobrepasa drásticamente la capacidad de carga (K) del hábitat, ¿qué desenlace ecológico sobreviene?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Degradación irreversible de los recursos vegetales seguida de una mortandad masiva (colapso demográfico por hambruna)",
                                "La capacidad de carga se duplica automáticamente",
                                "Los animales aprenden a alimentarse de rocas",
                                "No pasa nada porque la naturaleza no tiene límites"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El sobrepastoreo destruye la resiliencia del suelo y la biomasa primaria, precipitando el colapso de la población consumidora."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // QUÍMICA BACHILLERATO POR MADUREZ (10.º - 11.º / DGEC)
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_quim_bxm_transfer_reactivo_limitante",
                            conceptId = "cr_quim_bxm_c_estequiometria",
                            title = "Cálculo del Reactivo Limitante en la Mezcla Aire-Combustible",
                            prompt = "Para quemar 1 mol de octano (C₈H₁₈) se requieren 12.5 moles de oxígeno (O₂). Si en el cilindro hay 1 mol de octano pero solo 10 moles de O₂, ¿cuál es el reactivo limitante y qué gas tóxico se genera?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El oxígeno es el reactivo limitante; al haber combustión incompleta se genera monóxido de carbono (CO) e hidrocarburos crudos",
                                "El octano es el limitante y se genera helio",
                                "No se produce nada",
                                "El nitrógeno es el limitante"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La falta de O₂ estequiométrico provoca combustión incompleta, emitiendo CO letal y hollín por exceso relativo de combustible."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_quim_bxm_u02",
            track = CurriculumTrack.QUIMICA_BXM,
            unitNumber = 2,
            targetMonth = 6,
            monthName = "Junio",
            title = "Disoluciones Químicas y Concentración de Soluciones",
            description = "Molaridad (M = moles soluto / L disolución), porcentaje masa/masa (%m/m), partes por millón (ppm).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_quim_bxm_c_disoluciones",
                    conceptCode = "CR_QUIM_BXM_DISOLUCIONES",
                    title = "Unidades de Concentración Química",
                    description = "Cálculos cuantitativos de molaridad, soluto, solvente y dilución C₁V₁ = C₂V₂.",
                    targetMonth = 6,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_quim_bxm_molaridad_calc",
                            conceptId = "cr_quim_bxm_c_disoluciones",
                            title = "Cálculo de Molaridad (M)",
                            prompt = "Si se disuelven 2 moles de cloruro de sodio (NaCl) en suficiente agua para completar 4 litros de disolución, ¿cuál es su Molaridad?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("0.5 M (2 moles ÷ 4 L = 0.5 mol/L)", "2.0 M", "8.0 M", "0.25 M"),
                            correctOptionIndex = 0,
                            explanation = "Molaridad = Moles de soluto / Litros de solución = 2 mol / 4 L = 0.5 M."
                        ),
                        InteractiveTaskData(
                            id = "task_quim_bxm_transfer_dilucion_acido_bateria",
                            conceptId = "cr_quim_bxm_c_disoluciones",
                            title = "Protocolo de Seguridad al Diluir Ácido Sulfúrico",
                            prompt = "Al preparar electrolito para baterías con ácido sulfúrico concentrado y agua destilada, ¿cuál es la regla de oro de laboratorio obligatoria y por qué?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Verter SIEMPRE el ácido lentamente sobre el agua (nunca agua sobre el ácido), porque la reacción es fuertemente exotérmica y el agua sobre ácido herviría salpicando ácido puro al rostro",
                                "Echar el agua rápido sobre el ácido",
                                "Mezclarlos en un recipiente cerrado de plástico delgado",
                                "Calentar el ácido al fuego primero"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La hidratación del H₂SO₄ libera calor violento: el gran volumen de agua disipa la energía; al revés provoca ebullición explosiva del ácido."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_quim_bxm_u03",
            track = CurriculumTrack.QUIMICA_BXM,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Enlace Químico, Polaridad y Fuerzas Intermoleculares",
            description = "Estructura de Lewis, geometría molecular (RPECV), polaridad de enlace y puentes de hidrógeno.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_quim_bxm_c_fuerzas_intermoleculares",
                    conceptCode = "CR_QUIM_BXM_FUERZAS_INTERMOLECULARES",
                    title = "Puentes de Hidrógeno y Solubilidad 'Lo semejante disuelve a lo semejante'",
                    description = "Fuerzas dipolo-dipolo, dispersión de London y constantes dieléctricas.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_quim_bxm_puentes_hidrogeno",
                            conceptId = "cr_quim_bxm_c_fuerzas_intermoleculares",
                            title = "Puentes de Hidrógeno en el Agua",
                            prompt = "¿Con cuáles tres elementos altamente electronegativos debe enlazarse el hidrógeno para formar puentes de hidrógeno intermoleculares?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Nitrógeno (N), Oxígeno (O) y Flúor (F)", "Carbono, Silicio y Fósforo", "Hierro, Cobre y Zinc", "Sodio, Potasio y Cloro"),
                            correctOptionIndex = 0,
                            explanation = "El pequeño radio atómico y altísima electronegatividad de N, O y F permiten la formación de enlaces por puente de H."
                        ),
                        InteractiveTaskData(
                            id = "task_quim_bxm_transfer_inmiscibilidad_aceite_agua",
                            conceptId = "cr_quim_bxm_c_fuerzas_intermoleculares",
                            title = "Inmiscibilidad entre Aceite Lubricante y Agua",
                            prompt = "A nivel químico molecular, ¿por qué el aceite de motor (hidrocarburo no polar) y el agua (solvente altamente polar) no se mezclan jamás de manera espontánea?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Las moléculas de agua forman fuertes puentes de hidrógeno entre sí, excluyendo a las moléculas no polares de hidrocarburos que solo interactúan mediante débiles fuerzas de London",
                                "Porque el aceite es un metal pesado",
                                "Porque el agua tiene carga nuclear negativa",
                                "Por la velocidad a la que giran"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Bajo el principio termodinámico 'lo semejante disuelve a lo semejante', la cohesión intermolecular del agua repele hidrocarburos no polares."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_quim_bxm_u04",
            track = CurriculumTrack.QUIMICA_BXM,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Química Orgánica: Hidrocarburos, Grupos Funcionales y Polímeros",
            description = "Alcanos, alquenos, alquinos, isomería, alcoholes, ésteres y síntesis de polímeros elastómeros.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_quim_bxm_c_organica",
                    conceptCode = "CR_QUIM_BXM_ORGANICA",
                    title = "Nomenclatura IUPAC y Grupos Funcionales Orgánicos",
                    description = "Cadenas carbonadas, enlaces simples vs dobles y propiedades de elastómeros sintéticos.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_quim_bxm_alcanos_formula",
                            conceptId = "cr_quim_bxm_c_organica",
                            title = "Fórmula General de los Alcanos Saturados",
                            prompt = "¿Cuál es la fórmula general de los hidrocarburos alcanos lineales saturados de cadena abierta?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("C_n H_(2n+2)", "C_n H_(2n)", "C_n H_(2n-2)", "C_n H_n"),
                            correctOptionIndex = 0,
                            explanation = "Los alcanos presentan enlaces simples carbono-carbono y saturación completa con fórmula C_n H_(2n+2) (ej. Metano CH₄, Octano C₈H₁₈)."
                        ),
                        InteractiveTaskData(
                            id = "task_quim_bxm_transfer_vulcanizacion_caucho",
                            conceptId = "cr_quim_bxm_c_organica",
                            title = "Química de la Vulcanización de Neumáticos (Goodyear)",
                            prompt = "En la fabricación de neumáticos automotrices, ¿en qué consiste la reacción química de vulcanización descubierta por Charles Goodyear?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Calentar el caucho natural o sintético con azufre para crear puentes cruzados covalentes de disulfuro entre las cadenas poliméricas, otorgando elasticidad, resistencia térmica y durabilidad",
                                "Pintar la llanta con carbón negro para que se vea oscura",
                                "Sumergir la goma en ácido para derretirla",
                                "Quemar el caucho hasta cenizas"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Los enlaces cruzados de azufre transforman un polímero viscoso y pegajoso en un elastómero termoestable de alta tenacidad."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // FÍSICA BACHILLERATO POR MADUREZ (10.º - 11.º / DGEC)
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_fis_bxm_calor_especifico_formula",
                            conceptId = "cr_fis_bxm_c_termo",
                            title = "Ecuación Fundamental de la Calorimetría",
                            prompt = "¿Qué fórmula calcula la cantidad de calor absorbida o cedida por un cuerpo de masa m y calor específico c al experimentar un cambio de temperatura ΔT?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Q = m × c × ΔT", "Q = m ÷ (c × ΔT)", "Q = m × c²", "Q = P × V"),
                            correctOptionIndex = 0,
                            explanation = "Q = m × c × ΔT es la fórmula cuantitativa de transferencia de calor sensible sin cambio de fase."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_fis_bxm_u03",
            track = CurriculumTrack.FISICA_BXM,
            unitNumber = 3,
            targetMonth = 7,
            monthName = "Julio",
            title = "Trabajo Mecánico, Energía Cinética y Potencia (Watts/HP)",
            description = "W = F × d × cos(θ), teorema del trabajo y la energía cinética (W_neto = ΔEc), potencia mecánica y caballos de fuerza.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_fis_bxm_c_trabajo_potencia",
                    conceptCode = "CR_FIS_BXM_TRABAJO_POTENCIA",
                    title = "Teorema Trabajo-Energía y Potencia Mecánica",
                    description = "Relación entre Joules, Watts y Caballos de Fuerza (1 HP = 746 Watts).",
                    targetMonth = 7,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_fis_bxm_potencia_hp_calc",
                            conceptId = "cr_fis_bxm_c_trabajo_potencia",
                            title = "Conversión de Potencia: Watts a Caballos de Fuerza",
                            prompt = "Un motor eléctrico genera una potencia continua de 74.600 Watts (74.6 kW). ¿A cuántos Caballos de Fuerza (HP) equivale?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("100 HP (74600 W ÷ 746 W/HP = 100 HP)", "10 HP", "746 HP", "50 HP"),
                            correctOptionIndex = 0,
                            explanation = "Dado que 1 HP = 746 W: 74.600 W / 746 W/HP = 100 HP mecánicos."
                        ),
                        InteractiveTaskData(
                            id = "task_fis_bxm_transfer_energia_cinetica_velocidad",
                            conceptId = "cr_fis_bxm_c_trabajo_potencia",
                            title = "Energía Cinética Cuadrática en Choque Frontal",
                            prompt = "Si un vehículo duplica su velocidad de 40 km/h a 80 km/h, ¿por cuánto se multiplica su energía cinética destructiva (Ec = 1/2 mv²)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Se cuadruplica exactamente (se multiplica por 4, porque Ec depende de v² -> 2² = 4)",
                                "Se duplica (se multiplica por 2)",
                                "Permanece igual",
                                "Se multiplica por 8"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La energía cinética depende del cuadrado de la velocidad; duplicar la velocidad cuadruplica la energía que debe disiparse en el choque."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_fis_bxm_u04",
            track = CurriculumTrack.FISICA_BXM,
            unitNumber = 4,
            targetMonth = 9,
            monthName = "Septiembre",
            title = "Electromagnetismo y Circuitos Eléctricos Avanzados",
            description = "Ley de Coulomb, campo eléctrico, Ley de Ohm, Leyes de Kirchhoff y potencia disipada por efecto Joule.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_fis_bxm_c_kirchhoff",
                    conceptCode = "CR_FIS_BXM_KIRCHHOFF",
                    title = "Leyes de Kirchhoff y Conservación de Carga y Energía",
                    description = "Ley de Nodos (∑I_in = ∑I_out) y Ley de Mallas (∑V = 0) en circuitos de corriente continua.",
                    targetMonth = 9,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_fis_bxm_ley_nodos_kirchhoff",
                            conceptId = "cr_fis_bxm_c_kirchhoff",
                            title = "Primera Ley de Kirchhoff (Ley de Nodos)",
                            prompt = "En un nodo eléctrico ingresan dos corrientes: I₁ = 8 A e I₂ = 5 A. Si una rama de salida drena I₃ = 6 A, ¿cuánta corriente fluye por la segunda rama de salida I₄?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("7 A (8 + 5 = 13 A entrantes; 13 - 6 = 7 A)", "13 A", "1 A", "19 A"),
                            correctOptionIndex = 0,
                            explanation = "Por conservación de la carga eléctrica en el nodo: ∑I_entrantes = ∑I_salientes -> 8 + 5 = 6 + I₄ -> I₄ = 7 A."
                        ),
                        InteractiveTaskData(
                            id = "task_fis_bxm_transfer_efecto_joule_calibre",
                            conceptId = "cr_fis_bxm_c_kirchhoff",
                            title = "Efecto Joule y Selección de Calibre de Cable Automotriz",
                            prompt = "Un accesorio de alta demanda consume I = 30 A. Si se conecta con un cable delgado de resistencia parásita R = 0.5 Ω en vez de calibre grueso (0.02 Ω), ¿cuánta potencia se disipa en calor peligroso en el cable (P = I² × R)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "450 Watts de calor continuo (30² × 0.5 = 900 × 0.5 = 450 W), suficiente para derretir el aislante e iniciar un incendio",
                                "15 Watts",
                                "45 Watts",
                                "1.5 Watts"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El calentamiento Joule depende de I²: 900 × 0.5 Ω disipa 450 W en el arnés, creando un riesgo crítico de fuego."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // ESTUDIOS SOCIALES BACHILLERATO POR MADUREZ (DGEC)
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_soc_bxm_transfer_constitucion_49_innovaciones",
                            conceptId = "cr_soc_bxm_c_cr_siglo_xx",
                            title = "Innovaciones Institucionales de la Constitución de 1949",
                            prompt = "Además del sufragio femenino y la abolición del ejército, ¿cuál de los siguientes órganos fue creado con rango constitucional independiente por la Carta Magna de 1949?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El Tribunal Supremo de Elecciones (TSE) con rango de cuarto poder independiente y la Contraloría General de la República",
                                "El Ministerio de Guerra",
                                "El Banco Central Europeo",
                                "La Corte Internacional de La Haya"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El constituyente de 1949 blindó la pureza del sufragio erigiendo al TSE con independencia absoluta respecto a los poderes del Estado."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc_bxm_u02",
            track = CurriculumTrack.SOCIALES_BXM,
            unitNumber = 2,
            targetMonth = 6,
            monthName = "Junio",
            title = "La Guerra Fría y el Orden Geopolítico Mundial (1945-1991)",
            description = "Bipolaridad (EE.UU. vs URSS), carrera armamentista, crisis de los misiles, descolonización y caída del bloque soviético.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc_bxm_c_guerra_fria",
                    conceptCode = "CR_SOC_BXM_GUERRA_FRIA",
                    title = "Bipolaridad y Conflictos Regionales en la Guerra Fría",
                    description = "Impacto del enfrentamiento este-oeste en Centroamérica (guerras civiles y crisis de los 80).",
                    targetMonth = 6,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc_bxm_muro_berlin",
                            conceptId = "cr_soc_bxm_c_guerra_fria",
                            title = "Caída del Muro de Berlín (1989)",
                            prompt = "¿Qué hito histórico simbolizó el colapso del bloque soviético y el preludio del fin de la Guerra Fría?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "La caída del Muro de Berlín el 9 de noviembre de 1989",
                                "El ataque a Pearl Harbor",
                                "La Revolución Francesa",
                                "La firma del Tratado de Versalles"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La apertura y demolición del Muro de Berlín precipitó la reunificación alemana y la desintegración de la URSS en 1991."
                        ),
                        InteractiveTaskData(
                            id = "task_soc_bxm_transfer_plan_paz_esquipulas",
                            conceptId = "cr_soc_bxm_c_guerra_fria",
                            title = "El Plan de Paz de Esquipulas II y el Nobel de la Paz de 1987",
                            prompt = "En medio de las guerras civiles centroamericanas de los años 80, ¿qué trascendencia tuvo el liderazgo de Costa Rica encabezado por Óscar Arias Sánchez?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "La redacción y firma del Plan de Paz de Esquipulas II ('Procedimiento para establecer la paz firme y duradera en Centroamérica'), resolviendo el conflicto sin intervención militar extranjera",
                                "Comprar armas para intervenir en Nicaragua",
                                "Cerrar las fronteras marítimas permanentemente",
                                "La anexión de El Salvador a Costa Rica"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El Plan de Paz forjó el desarme y elecciones democráticas libres en Centroamérica, otorgándole a Costa Rica el Premio Nobel de la Paz en 1987."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc_bxm_u03",
            track = CurriculumTrack.SOCIALES_BXM,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "De la Crisis de 1980 al Modelo de Promoción de Exportaciones (PAE)",
            description = "Agotamiento del modelo ISI, crisis cambiaria de Carazo, Programas de Ajuste Estructural (PAE I, II y III) y apertura comercial.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc_bxm_c_ajuste_estructural",
                    conceptCode = "CR_SOC_BXM_AJUSTE_ESTRUCTURAL",
                    title = "Los Programas de Ajuste Estructural (PAE)",
                    description = "Reducción del déficit fiscal, desgravación arancelaria y fomento de exportaciones no tradicionales.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc_bxm_exportaciones_no_tradicionales",
                            conceptId = "cr_soc_bxm_c_ajuste_estructural",
                            title = "Diversificación Productiva no Tradicional",
                            prompt = "¿Qué nuevos rubros de exportación sustituyeron la hegemonía del café y banano tras las reformas de apertura económica de finales del siglo XX?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Piña, plantas ornamentales, tubérculos, textiles, turismo y zonas francas de microelectrónica (Intel)",
                                "Carbón y trigo",
                                "Armas de combate",
                                "Automóviles diésel pesados"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El Estado diversificó la canasta exportadora mediante los Certificados de Abono Tributario (CAT) y la atracción de IED."
                        ),
                        InteractiveTaskData(
                            id = "task_soc_bxm_transfer_impacto_social_pae",
                            conceptId = "cr_soc_bxm_c_ajuste_estructural",
                            title = "Efectos Sociales y Desigualdad del Modelo de Apertura",
                            prompt = "Si bien los PAE estabilizaron la inflación y aumentaron las exportaciones, ¿cuál fue su impacto negativo documentado en el Estado de la Nación?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Un aumento en la desigualdad del ingreso (alza en el coeficiente de Gini), deterioro en inversión de infraestructura pública y precarización laboral del sector informal",
                                "La eliminación total de la pobreza",
                                "La desaparición de las universidades",
                                "El fin del comercio internacional"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Los recortes del gasto público crearon una 'economía dual': dinamismo en zonas francas frente a rezago en regiones periféricas y zonas rurales."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc_bxm_u04",
            track = CurriculumTrack.SOCIALES_BXM,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Costa Rica en el Siglo XXI: Desafíos de Equidad, Gobernanza y Sostenibilidad",
            description = "Déficit fiscal, regla fiscal, informalidad laboral, transición demográfica y liderazgo ambiental global de Costa Rica.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc_bxm_c_siglo_xxi_desafios",
                    conceptCode = "CR_SOC_BXM_SIGLO_XXI_DESAFIOS",
                    title = "Retos Estructurales del Bicentenario",
                    description = "Sostenibilidad de la seguridad social de la CCSS y educación para la cuarta revolución industrial.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc_bxm_informalidad_laboral",
                            conceptId = "cr_soc_bxm_c_siglo_xxi_desafios",
                            title = "El Reto de la Informalidad Laboral en Costa Rica",
                            prompt = "Cerca del 40% de la fuerza laboral en Costa Rica trabaja en el sector informal. ¿Cuál es la consecuencia directa para el trabajador y el país?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Carecen de seguro de salud y pensiones de la CCSS, no reciben salario mínimo ni póliza de riesgos, y el Estado deja de percibir tributos esenciales",
                                "Ganan el doble de salario sin trabajar",
                                "No tienen ningún problema económico",
                                "Se jubilan a los 30 años"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La informalidad condena a las familias a la vulnerabilidad social y desfinancia el régimen de pensiones de Invalidez, Vejez y Muerte (IVM)."
                        ),
                        InteractiveTaskData(
                            id = "task_soc_bxm_transfer_transicion_energetica_clase_mundial",
                            conceptId = "cr_soc_bxm_c_siglo_xxi_desafios",
                            title = "Liderazgo de Costa Rica en la Transición Ecológica Mundial",
                            prompt = "Costa Rica recibió el premio 'Campeones de la Tierra' de la ONU. ¿Qué modelo de desarrollo representa el país ante la comunidad internacional?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Demostrar que es viable revertir la deforestación (pasando del 21% al más del 54% de cobertura boscosa) combinando matriz eléctrica renovable y turismo ecológico de alto valor",
                                "Un país basado en la extracción intensiva de petróleo",
                                "Una nación con fábricas de carbón",
                                "Un país que no protege sus recursos hídricos"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El pago por servicios ambientales (PSA) y la red de parques nacionales consolidaron a Costa Rica como faro global de sostenibilidad."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // EDUCACIÓN CÍVICA BACHILLERATO POR MADUREZ (DGEC)
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_civ_bxm_transfer_pesos_contrapesos",
                            conceptId = "cr_civ_bxm_c_democracia",
                            title = "El Sistema de Frenos y Contrapesos (Checks and Balances)",
                            prompt = "En la arquitectura institucional de Costa Rica, ¿cómo ejerce el Poder Judicial control sobre el Poder Legislativo?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "A través de la Sala Constitucional (Sala IV), la cual puede declarar inconstitucional y nula cualquier ley aprobada que viole la Carta Magna o tratados de derechos humanos",
                                "Disolviendo el Congreso con la policía",
                                "Cobrando multas a los diputados",
                                "Reescribiendo las leyes a su gusto sin consultar"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El control de constitucionalidad encomendado a la Sala IV garantiza la supremacía incondicional de la Constitución sobre los actos legislativos."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_civ_bxm_u02",
            track = CurriculumTrack.CIVICA_BXM,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Sistemas Políticos y Regímenes Contemporáneos",
            description = "Democracia liberal vs autoritarismo, fascismo, dictadura militar, socialismo democrático, teocracia y monarquías constitucionales.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ_bxm_c_sistemas_politicos",
                    conceptCode = "CR_CIV_BXM_SISTEMAS_POLITICOS",
                    title = "Regímenes Políticos Comparados",
                    description = "Rasgos definitorios de regímenes democráticos vs regímenes autoritarios y totalitarios.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ_bxm_rasgo_autoritarismo",
                            conceptId = "cr_civ_bxm_c_sistemas_politicos",
                            title = "Rasgo Inconfundible de un Régimen Autoritario",
                            prompt = "¿Cuál de las siguientes condiciones es típica de un régimen político autoritario o dictatorial?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Concentración del poder en una sola persona o partido hegemónico, anulación de la separación de poderes y persecución de la oposición y prensa libre",
                                "Elecciones periódicas transparentes y competitivas",
                                "Plena vigencia del Hábeas Corpus y libertad de reunión",
                                "Rendición de cuentas de todos los gobernantes"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El autoritarismo suprime el pluralismo político, coarta la libertad de expresión y desmantela el control judicial independiente."
                        ),
                        InteractiveTaskData(
                            id = "task_civ_bxm_transfer_monarquia_constitucional",
                            conceptId = "cr_civ_bxm_c_sistemas_politicos",
                            title = "Monarquía Parlamentaria (Reino Unido / España)",
                            prompt = "¿Cómo se distribuye la autoridad política en una monarquía parlamentaria democrática contemporánea?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El Rey o Reina es Jefe de Estado simbólico y ceremonial ('el rey reina pero no gobierna'), mientras que el Primer Ministro elegido por el parlamento ejerce el poder ejecutivo real",
                                "El Monarca tiene poder absoluto sobre la vida de los ciudadanos",
                                "No existe parlamento ni elecciones",
                                "El ejército gobierna por decreto divino"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La soberanía reside en el pueblo a través del parlamento; la monarquía es una institución neutral representativa de la unidad del Estado."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_civ_bxm_u03",
            track = CurriculumTrack.CIVICA_BXM,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Mecanismos Constitucionales de Protección de Derechos: Sala IV y Defensoría",
            description = "El Recurso de Amparo, el Hábeas Corpus, la Acción de Inconstitucionalidad y la Defensoría de los Habitantes (Ley 7428).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ_bxm_c_sala_iv",
                    conceptCode = "CR_CIV_BXM_SALA_IV",
                    title = "Garantías Procesales de la Justicia Constitucional",
                    description = "Acceso directo, informal y expedito sin necesidad de abogado ante violaciones a derechos fundamentales.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ_bxm_recurso_amparo",
                            conceptId = "cr_civ_bxm_c_sala_iv",
                            title = "El Recurso de Amparo en Costa Rica",
                            prompt = "¿Qué derechos tutela el Recurso de Amparo ante la Sala Constitucional?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Todos los derechos y libertades fundamentales consagradas en la Constitución y tratados internacionales, salvo la libertad individual tutelada por el Hábeas Corpus",
                                "Únicamente la libertad de tránsito",
                                "Disputas de cobro de deudas mercantiles",
                                "Divorcios y herencias"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El Amparo protege la salud, el medio ambiente sano, la educación, la igualdad, el debido proceso y la no discriminación."
                        ),
                        InteractiveTaskData(
                            id = "task_civ_bxm_transfer_amparo_salud_ccss",
                            conceptId = "cr_civ_bxm_c_sala_iv",
                            title = "Exigibilidad Judicial del Derecho a la Salud y Medicamentos",
                            prompt = "Si la CCSS niega injustificadamente a un paciente un medicamento vital o una cirugía urgente para salvar su vida, ¿qué mecanismo constitucional expedito ordena su entrega inmediata?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Interponer un Recurso de Amparo ante la Sala Constitucional, la cual emitirá una medida cautelar obligatoria e inmediata en resguardo del derecho a la vida",
                                "Esperar años en un juicio civil ordinario",
                                "Pedir prestado dinero a un banco",
                                "No hay ningún recurso legal disponible"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La Sala IV ha consolidado una doctrina tutelar garantista del derecho humano a la vida y la salud de los asegurados."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_civ_bxm_u04",
            track = CurriculumTrack.CIVICA_BXM,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Cultura Política, Comunicación Digital y el Deber del Sufragio",
            description = "Desinformación (fake news), polarización, algoritmos en redes sociales, financiamiento de partidos políticos y civismo ético.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ_bxm_c_cultura_digital",
                    conceptCode = "CR_CIV_BXM_CULTURA_DIGITAL",
                    title = "Pensamiento Crítico ante la Desinformación Electoral",
                    description = "Contrastación de fuentes, verificación de hechos (fact-checking) y responsabilidad cívica digital.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ_bxm_fact_checking_redes",
                            conceptId = "cr_civ_bxm_c_cultura_digital",
                            title = "Detección de Noticias Falsas en Campañas Electorales",
                            prompt = "Antes de compartir en redes sociales un mensaje alarmante sobre un candidato o política pública, ¿cuál es el deber del ciudadano crítico?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Verificar la fecha, autoría, contrastar en sitios de noticias formales o medios de fact-checking y revisar fuentes primarias oficiales (TSE, INEC, leyes)",
                                "Compartirlo inmediatamente si confirma mis propios prejuicios",
                                "Inventar más detalles para hacerlo viral",
                                "Creer todo lo que diga un meme sin autor"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La higiene informativa y la verificación rigurosa de fuentes primarias blindan a la democracia frente a la manipulación masiva."
                        ),
                        InteractiveTaskData(
                            id = "task_civ_bxm_transfer_voto_informado_programa",
                            conceptId = "cr_civ_bxm_c_cultura_digital",
                            title = "El Voto Informado y el Análisis del Plan de Gobierno",
                            prompt = "¿Cuál es el criterio ético y cívico más maduro para ejercer el derecho al voto en una elección presidencial o municipal?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Estudiar a fondo el Plan de Gobierno, la viabilidad técnica y presupuestaria de las propuestas, y la trayectoria ética y solvencia del equipo postulante",
                                "Votar por la bandera del partido de mis abuelos sin leer nada",
                                "Votar por quien regale más camisetas o comida",
                                "Votar al azar cerrando los ojos"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El sufragio informado exige evaluar la coherencia programática, solvencia técnica y apego a los valores democráticos republicanos."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // INGLÉS BACHILLERATO POR MADUREZ (C1 MASTERY / DGEC)
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_ing_bxm_transfer_inference_context",
                            conceptId = "cr_ing_bxm_c_reading",
                            title = "Contextual Lexical Inference in Battery Patents",
                            prompt = "Read: 'The electrolyte solution exhibits high volatility at elevated temperatures, thereby precipitating thermal runaway.' What does 'precipitating' mean here?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Triggering or causing an event rapidly",
                                "Raining water from clouds",
                                "Freezing solid into ice",
                                "Cleaning the metal surface"
                            ),
                            correctOptionIndex = 0,
                            explanation = "In technical English context, 'to precipitate' means to trigger or accelerate an adverse phenomenon suddenly."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing_c1_u02",
            track = CurriculumTrack.INGLES_BXM,
            unitNumber = 2,
            targetMonth = 4,
            monthName = "Abril",
            title = "C1 Advanced Syntactic Mastery: Inversion, Clefts & Mixed Conditionals",
            description = "Estructuras gramaticales complejas C1 para peritajes forenses, oraciones hendidas e inversión tras adverbiales negativos.",
            estimatedLessons = 10,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing_c1_c_inversion_cleft",
                    conceptCode = "CR_ING_C1_INVERSION_CLEFT",
                    title = "Inversion and Cleft Structures for Forensic Emphasis",
                    description = "Inversión sintáctica obligatoria (under no circumstances, not only) y oraciones hendidas para identificar causas raíz.",
                    targetMonth = 4,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing_c1_negative_inversion",
                            conceptId = "cr_ing_c1_c_inversion_cleft",
                            title = "Negative Adverbial Inversion in Safety Protocol",
                            prompt = "Complete the official EV safety protocol sentence: 'Under no circumstances _______ the high-voltage interlock loop while the contactor is energized.'",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "should the technician probe",
                                "the technician should probe",
                                "the technician probes",
                                "should probe the technician"
                            ),
                            correctOptionIndex = 0,
                            explanation = "When an English sentence begins with a negative or restrictive adverbial like 'Under no circumstances', subject-auxiliary inversion is mandatory ('should the technician probe')."
                        )
                    )
                ),
                CurriculumConceptData(
                    id = "cr_ing_c1_c_mixed_conditionals",
                    conceptCode = "CR_ING_C1_MIXED_CONDITIONALS",
                    title = "Mixed Conditionals and Root-Cause Counterfactuals",
                    description = "Análisis contrafáctico de causas pasadas con consecuencias en el presente o condiciones permanentes en fallos históricos.",
                    targetMonth = 4,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing_c1_root_cause_conditional",
                            conceptId = "cr_ing_c1_c_mixed_conditionals",
                            title = "Forensic Root-Cause Mixed Conditional",
                            prompt = "Select the grammatically correct C1 mixed conditional analyzing an engine failure: 'If the mechanic _______ the cylinder head to 85 Nm yesterday, the engine _______ coolant right now.'",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "had torqued / would not be leaking",
                                "torqued / would not leak",
                                "had torqued / would not have leaked",
                                "would torque / had not leaked"
                            ),
                            correctOptionIndex = 0,
                            explanation = "A past action (Past Perfect: 'had torqued') generating an ongoing present state (would + continuous infinitive: 'would not be leaking') is a canonical Type 3 + Type 2 mixed conditional."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing_c1_u03",
            track = CurriculumTrack.INGLES_BXM,
            unitNumber = 3,
            targetMonth = 5,
            monthName = "Mayo",
            title = "C1 Professional STEM Diagnostics & Telemetry Reports",
            description = "Lectura de telemetría CAN-bus, decodificación de tramas UDS y redacción de dictámenes técnicos con lenguaje cauto (hedging).",
            estimatedLessons = 10,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing_c1_c_telemetry_hedging",
                    conceptCode = "CR_ING_C1_TELEMETRY_HEDGING",
                    title = "Technical Telemetry Interpretation & Forensic Hedging",
                    description = "Uso de lenguaje cauto y matizado (hedging) en dictámenes de ingeniería sin juicios de valor subjetivos.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing_c1_forensic_hedging",
                            conceptId = "cr_ing_c1_c_telemetry_hedging",
                            title = "C1 Forensic Diagnostic Hedging",
                            prompt = "Which statement represents acceptable C1 forensic engineering style avoiding unprofessional absolute claims?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "The telemetry logs suggest that excessive thermal cycles may have contributed to premature IGBT solder fatigue.",
                                "The driver definitely destroyed the whole inverter by driving recklessly.",
                                "It is 100% obvious that the vehicle was ruined by bad maintenance.",
                                "The shop clearly forgot how to repair electrical systems."
                            ),
                            correctOptionIndex = 0,
                            explanation = "C1 professional engineering English mandates cautious hedging ('suggest that... may have contributed to...') to produce legally defensible affidavits."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing_c1_u04",
            track = CurriculumTrack.INGLES_BXM,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "C1 Global Engineering Affidavits, ISO 26262 & Forensic Disclosure",
            description = "Redacción de declaraciones periciales juradas (affidavits), seguridad funcional ISO 26262 (ASIL D) y dictámenes periciales vinculantes.",
            estimatedLessons = 10,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing_c1_c_affidavit_iso26262",
                    conceptCode = "CR_ING_C1_AFFIDAVIT_ISO26262",
                    title = "Forensic Affidavits and Functional Safety Compliance",
                    description = "Formulación jurídica de atestados periciales internacionales con trazabilidad de normas ISO/SAE.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing_c1_affidavit_attestation",
                            conceptId = "cr_ing_c1_c_affidavit_iso26262",
                            title = "Legal Affidavit Solemn Attestation Clause",
                            prompt = "Which phrase constitutes the standard C1 legal opening for an expert witness affidavit submitted in international arbitration?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "'I, the undersigned forensic engineer, hereby depose and state under penalty of perjury that the findings herein reflect rigorous empirical analysis...'",
                                "'Hey judge, I checked the car and it is really bad...'",
                                "'In my humble personal opinion, someone messed up the brakes...'",
                                "'I am writing this letter because the owner asked me to...'"
                            ),
                            correctOptionIndex = 0,
                            explanation = "International forensic affidavits require solemn attestation under penalty of perjury, establishing expert jurisdiction."
                        ),
                        InteractiveTaskData(
                            id = "task_ing_c1_transfer_iso26262_asil_d",
                            conceptId = "cr_ing_c1_c_affidavit_iso26262",
                            title = "ISO 26262 ASIL D Safety Integrity Level Violation",
                            prompt = "In an expert report investigating autonomous braking failure: 'The steer-by-wire controller lacked hardware redundancy, violating ASIL D single-point fault metric (SPFM ≥ 99%).' What is the legal implication?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El fabricante incurrió en defecto de diseño no subsanable al violar la métrica estricta de seguridad funcional ISO 26262 para sistemas de criticidad letal (ASIL D)",
                                "El sistema funciona bien y el conductor tuvo la culpa",
                                "ASIL D significa que el auto es muy veloz",
                                "Es un detalle estético sin relevancia judicial"
                            ),
                            correctOptionIndex = 0,
                            explanation = "ASIL D es el Automotive Safety Integrity Level más riguroso; no cumplir con SPFM ≥ 99% demuestra negligencia de diseño del fabricante en cortes internacionales."
                        )
                    )
                )
            )
        )
    )
}
