package com.elysium369.meet.education.data

import com.elysium369.meet.education.domain.EpistemicTruthState

/**
 * Catálogo Curricular Completo para 8.º Año (III Ciclo / Ujarrás) — MEP Costa Rica 2026.
 * Incluye Matemática 8, Dibujo Técnico 8, Español 8, Ciencias 8, Estudios Sociales 8, Cívica 8 e Inglés 8.
 * Estructurado con múltiples unidades, conceptos rigurosos y tareas de transferencia PISA.
 */
object SecondaryGrade8CurriculumSeed {

    // ══════════════════════════════════════════════════════════════════════════
    // 8.º AÑO: MATEMÁTICA (UJARRÁS)
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_mat8_transfer_formula_potencia",
                            conceptId = "cr_mat8_c_algebra",
                            title = "Modelado Algebraico de Costo Operativo",
                            prompt = "El costo total de un servicio es C(h) = 15000 + 8500h, donde h representa las horas de trabajo. ¿Cuánto costará una reparación de 4 horas?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("₡49.000 (15000 + 8500 × 4 = 15000 + 34000)", "₡45.000", "₡34.000", "₡52.000"),
                            correctOptionIndex = 0,
                            explanation = "Evaluando el polinomio lineal para h = 4: 15.000 + (8.500 × 4) = 15.000 + 34.000 = ₡49.000."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat8_u02",
            track = CurriculumTrack.MATEMATICA_8,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Polinomios: Suma, Resta y Multiplicación de Monomio por Polinomio",
            description = "Propiedad distributiva, grado de un polinomio y valor numérico.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat8_c_polinomios",
                    conceptCode = "CR_MAT8_POLINOMIOS",
                    title = "Multiplicación Algebraica y Distributividad",
                    description = "a(b + c) = ab + ac y leyes de exponentes en productos de bases iguales.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat8_monomio_por_polinomio",
                            conceptId = "cr_mat8_c_polinomios",
                            title = "Producto de Monomio por Polinomio",
                            prompt = "¿Cuál es el resultado de multiplicar 3x(2x² - 5x + 4)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("6x³ - 15x² + 12x", "6x² - 15x + 12", "5x³ - 2x² + 7x", "6x³ + 15x² + 12x"),
                            correctOptionIndex = 0,
                            explanation = "Distribuyendo: 3x(2x²) = 6x³; 3x(-5x) = -15x²; 3x(4) = 12x."
                        ),
                        InteractiveTaskData(
                            id = "task_mat8_transfer_expresion_area",
                            conceptId = "cr_mat8_c_polinomios",
                            title = "Expresión Algebraica del Área de una Chapa Metálica",
                            prompt = "Una placa rectangular mide (x + 3) de base y 2x de altura. ¿Qué expresión algebraica describe su área superficial?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("2x² + 6x (Base × Altura = 2x(x + 3))", "2x² + 3", "3x + 3", "2x² + 6"),
                            correctOptionIndex = 0,
                            explanation = "Área = base × altura = 2x(x + 3) = 2x² + 6x."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat8_u03",
            track = CurriculumTrack.MATEMATICA_8,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Geometría: Teorema de Pitágoras y Triángulos Rectángulos",
            description = "Fórmula c² = a² + b², cálculo de hipotenusa, catetos y aplicaciones en diagonales.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat8_c_pitagoras",
                    conceptCode = "CR_MAT8_PITAGORAS",
                    title = "El Teorema de Pitágoras en Problemas Reales",
                    description = "Relación geométrica fundamental entre los catetos y la hipotenusa.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat8_pitagoras_calc",
                            conceptId = "cr_mat8_c_pitagoras",
                            title = "Cálculo de la Hipotenusa",
                            prompt = "En un triángulo rectángulo cuyos catetos miden a = 3 cm y b = 4 cm, ¿cuánto mide la hipotenusa c?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("5 cm (c = √(3² + 4²) = √(9 + 16) = √25 = 5)", "7 cm", "12 cm", "6 cm"),
                            correctOptionIndex = 0,
                            explanation = "c = √(a² + b²) = √(9 + 16) = √25 = 5 cm."
                        ),
                        InteractiveTaskData(
                            id = "task_mat8_transfer_escuadra_taller",
                            conceptId = "cr_mat8_c_pitagoras",
                            title = "Comprobación de Escuadra 3-4-5 en Montaje",
                            prompt = "Para verificar si una estructura soldada está a 90° exactos (en escuadra), un soldador mide 60 cm en un lado y 80 cm en el otro. ¿Cuánto debe medir la diagonal entre los dos extremos?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "100 cm (1 metro), aplicando el triángulo pitagórico 60² + 80² = 3600 + 6400 = 10000 = 100²",
                                "140 cm",
                                "120 cm",
                                "90 cm"
                            ),
                            correctOptionIndex = 0,
                            explanation = "√(60² + 80²) = √(3600 + 6400) = √10000 = 100 cm. Si la diagonal es exactamente 100 cm, el ángulo es de 90°."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat8_u04",
            track = CurriculumTrack.MATEMATICA_8,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Geometría de Transformaciones y Homotecias",
            description = "Traslaciones, reflexiones simétricas, rotaciones y homotecias (escalas).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat8_c_transformaciones",
                    conceptCode = "CR_MAT8_TRANSFORMACIONES",
                    title = "Isometrías y Escalas de Semejanza",
                    description = "Preservación de forma, ángulos y distancias en figuras geométricas.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat8_homotecia_factor",
                            conceptId = "cr_mat8_c_transformaciones",
                            title = "Factor de Homotecia en Ampliación",
                            prompt = "Si a un triángulo de lados 5, 7 y 9 se le aplica una homotecia con razón k = 3, ¿cuáles serán las medidas de sus nuevos lados?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("15, 21 y 27 (cada lado multiplicado por 3)", "8, 10 y 12", "5, 7 y 9", "1.6, 2.3 y 3"),
                            correctOptionIndex = 0,
                            explanation = "En una homotecia de razón k, todas las longitudes se multiplican por |k|: 5×3=15, 7×3=21, 9×3=27."
                        ),
                        InteractiveTaskData(
                            id = "task_mat8_transfer_zoom_cad",
                            conceptId = "cr_mat8_c_transformaciones",
                            title = "Factor de Escala en Software de Modelado CAD",
                            prompt = "En un plano mecánico dibujado a escala 1:20, una pieza mide 15 mm en la pantalla. ¿Cuál es la dimensión real en milímetros?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("300 mm (15 mm × 20)", "150 mm", "30 mm", "75 mm"),
                            correctOptionIndex = 0,
                            explanation = "En escala 1:20, la medida real es 20 veces mayor que en el dibujo: 15 mm × 20 = 300 mm (30 cm)."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 8.º AÑO: DIBUJO TÉCNICO (CAD)
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_art8_vistas_ortogonales",
                            conceptId = "cr_art_c_dibujo_tecnico",
                            title = "Vistas Principales en Proyección Diédrica",
                            prompt = "¿Cuáles son las tres vistas ortogonales normalizadas fundamentales para definir completamente un sólido en dibujo técnico?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Vista frontal (alzado), vista superior (planta) y vista lateral izquierda (perfil)",
                                "Perspectiva cónica, sombra y textura",
                                "Corte transversal y vista isométrica únicamente",
                                "Color, brillo y reflectividad"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El sistema diédrico estandarizado ISO y ANSI define el objeto mediante alzado, planta y perfil."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_dt8_u02",
            track = CurriculumTrack.DIBUJO_TECNICO_8,
            unitNumber = 2,
            targetMonth = 7,
            monthName = "Julio",
            title = "Instrumentos de Precisión: El Calibrador Pie de Rey (Vernier)",
            description = "Lectura en milímetros (apreciación 0.05 mm y 0.02 mm) y fracciones de pulgada (1/128\").",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_dt8_c_vernier",
                    conceptCode = "CR_DT8_VERNIER",
                    title = "Lectura y Calibración con el Pie de Rey",
                    description = "Medición de exteriores, interiores y profundidad con corredera graduada.",
                    targetMonth = 7,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_dt8_lectura_vernier_mm",
                            conceptId = "cr_dt8_c_vernier",
                            title = "Lectura de Nonio Métrico",
                            prompt = "En un pie de rey con apreciación 0.05 mm, el cero del nonio pasa de 14 mm y la 6.ª línea coincide exactamente con la regla. ¿Cuál es la medida?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("14.30 mm (14 + 6 × 0.05 = 14 + 0.30)", "14.60 mm", "14.06 mm", "20.00 mm"),
                            correctOptionIndex = 0,
                            explanation = "Cada división del nonio son 0.05 mm; la 6ta división aporta 6 × 0.05 = 0.30 mm. Sumado a 14 mm = 14.30 mm."
                        ),
                        InteractiveTaskData(
                            id = "task_dt8_transfer_tolerancia_cilindro",
                            conceptId = "cr_dt8_c_vernier",
                            title = "Verificación de Tolerancia de Diámetro de Cilindro",
                            prompt = "Un bulón de pistón tiene tolerancia especificada de 20.00 mm ± 0.02 mm. Si al medir con micrómetro marca 20.03 mm, ¿cuál es el diagnóstico?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Rechazado fuera de tolerancia (sobrepasa el límite máximo permitido de 20.02 mm)",
                                "Aceptado dentro del rango",
                                "La pieza no tiene tolerancia",
                                "El instrumento está defectuoso"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El rango permitido es [19.98 mm, 20.02 mm]. 20.03 mm excede la cota superior, provocando riesgo de agarrotamiento."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_dt8_u03",
            track = CurriculumTrack.DIBUJO_TECNICO_8,
            unitNumber = 3,
            targetMonth = 9,
            monthName = "Septiembre",
            title = "Proyección Axonométrica: Perspectiva Isométrica y Caballera",
            description = "Ejes isométricos a 120° (30° respecto a la horizontal), óvalos isométricos y trazado de círculos.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_dt8_c_isometrico",
                    conceptCode = "CR_DT8_ISOMETRICO",
                    title = "Construcción Geométrica en Perspectiva Isométrica",
                    description = "Uso de cartabón de 30°/60° y regla T para dibujo tridimensional.",
                    targetMonth = 9,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_dt8_ejes_isometricos",
                            conceptId = "cr_dt8_c_isometrico",
                            title = "Inclinación de los Ejes Isométricos",
                            prompt = "¿A cuántos grados respecto a la línea horizontal base se trazan los dos ejes laterales en una perspectiva isométrica normalizada?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("30 grados a cada lado", "45 grados", "60 grados", "90 grados"),
                            correctOptionIndex = 0,
                            explanation = "En proyección isométrica, los ejes x e y se inclinan a 30° sobre la horizontal, sumando 120° entre los tres ejes espaciales."
                        ),
                        InteractiveTaskData(
                            id = "task_dt8_transfer_isocirculo_valvula",
                            conceptId = "cr_dt8_c_isometrico",
                            title = "Trazado del Óvalo Isométrico (Círculo en Perspectiva)",
                            prompt = "En un dibujo isométrico, ¿por qué los orificios circulares de una culata o brida se dibujan como elipses u óvalos de 4 centros?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Porque la proyección angular de un círculo plano visto a 30° se deforma geométricamente en una elipse",
                                "Porque los huecos reales son ovalados",
                                "Por un error del compás",
                                "Para ahorrar espacio en el plano"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La proyección oblicua de una circunferencia euclidiana genera una elipse congruente con los ejes del plano isométrico."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_dt8_u04",
            track = CurriculumTrack.DIBUJO_TECNICO_8,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Normas de Acotado, Cortes y Secciones Técnicas (ISO 128)",
            description = "Líneas de cota, flechas, cifras, símbolos de diámetro (Ø), radio (R) y rayado de corte a 45°.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_dt8_c_acotado_cortes",
                    conceptCode = "CR_DT8_ACOTADO_CORTES",
                    title = "Reglas Universales de Acotado y Planos Seccionados",
                    description = "Cortes totales, medios cortes y representación de componentes mecánicos internos.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_dt8_simbolo_diametro",
                            conceptId = "cr_dt8_c_acotado_cortes",
                            title = "Símbolo Normalizado de Diámetro",
                            prompt = "¿Cuál es el símbolo técnico universal que precede a una cota para indicar que la medida corresponde al diámetro de un cilindro o perforación?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Ø (Phi / círculo tachado)", "R", "□", "#"),
                            correctOptionIndex = 0,
                            explanation = "El símbolo Ø representa el diámetro en cualquier plano técnico según la norma ISO 128."
                        ),
                        InteractiveTaskData(
                            id = "task_dt8_transfer_interpretacion_corte_motor",
                            conceptId = "cr_dt8_c_acotado_cortes",
                            title = "Lectura de Plano en Corte de Motor",
                            prompt = "En el plano técnico de un bloque de motor seccionado, ¿qué representa el rayado con líneas paralelas inclinadas a 45°?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El material sólido macizo que fue cortado por el plano imaginario de sección",
                                "Las partes huecas por donde pasa el aire",
                                "La zona donde hay una fuga de aceite",
                                "El cableado eléctrico de la bujía"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El rayado a 45° (hachurado) identifica inequívocamente las zonas macizas cortadas por el plano secante."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 8.º AÑO: ESPAÑOL
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_esp8_transfer_redaccion_dictamen",
                            conceptId = "cr_esp8_c_oracion_comp",
                            title = "Cohesión Sintáctica en Informe Pericial",
                            prompt = "Seleccione la formulación pericial sintácticamente impecable que vincula condición y consecuencia técnica:",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Si el sensor de oxígeno emite valores fuera de rango, la unidad de control incrementará la inyección de combustible.",
                                "El sensor fallando entonces la computadora inyecta más gasolina.",
                                "Porque el sensor falló pero inyecta combustible mucho.",
                                "Sensor malo entonces carro gastón."
                            ),
                            correctOptionIndex = 0,
                            explanation = "La estructura condicional subordinada ('Si..., [entonces]...') expresa la relación causal con precisión gramatical."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp8_u02",
            track = CurriculumTrack.ESPANOL_8,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "El Reportaje Periodístico y el Artículo de Opinión",
            description = "Investigación periodística, contrastación de fuentes, objetividad vs subjetividad y estructura piramidal.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp8_c_periodismo",
                    conceptCode = "CR_ESP8_PERIODISMO",
                    title = "Estructura del Reportaje y Juicio Crítico",
                    description = "Diferenciar entre hechos empíricos verificables y opiniones valorativas.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp8_hecho_vs_opinion",
                            conceptId = "cr_esp8_c_periodismo",
                            title = "Diferenciación entre Hecho y Opinión",
                            prompt = "¿Cuál de los siguientes enunciados representa un HECHO fáctico comprobable y no una mera opinión subjetiva?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "El informe del INS registró 120 accidentes de tránsito en la Ruta 32 durante el primer trimestre.",
                                "Los autos modernos son todos muy feos.",
                                "Manejar rápido es la mejor sensación del mundo.",
                                "Ese taller tiene un servicio aburrido."
                            ),
                            correctOptionIndex = 0,
                            explanation = "Un hecho contiene datos cuantitativos verificables por terceros a través de registros oficiales."
                        ),
                        InteractiveTaskData(
                            id = "task_esp8_transfer_auditoria_fuentes",
                            conceptId = "cr_esp8_c_periodismo",
                            title = "Evaluación Epistémica de Fuentes Técnicas",
                            prompt = "Al redactar un artículo técnico sobre fallos en vehículos eléctricos, ¿cuál fuente goza de máxima autoridad científica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Boletines de servicio oficiales del fabricante (TSB) y normativas técnicas de la SAE e ISO",
                                "Comentarios anónimos en foros de internet",
                                "Un video de TikTok sin autor identificado",
                                "El rumor de un vecino"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Los Technical Service Bulletins (TSB) e institutos de estandarización internacional son fuentes primarias vinculantes."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp8_u03",
            track = CurriculumTrack.ESPANOL_8,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Vicios del Lenguaje: Dequeísmo, Redundancia y Anfibología",
            description = "Corrección idiomática en documentos oficiales: queísmo/dequeísmo, pleonasmos y ambigüedad sintáctica.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp8_c_vicios_lenguaje",
                    conceptCode = "CR_ESP8_VICIOS_LENGUAJE",
                    title = "Detección y Corrección de Vicios de Dicción",
                    description = "Técnicas de sustitución para erradicar el dequeísmo y ambigüedades.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp8_dequeismo_prueba",
                            conceptId = "cr_esp8_c_vicios_lenguaje",
                            title = "Prueba del Reemplazo para Evitar el Dequeísmo",
                            prompt = "¿Cuál oración contiene un uso CORRECTO de la preposición 'de que'?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "El ingeniero está seguro de que la pieza cumple la norma (¿De qué está seguro? De eso).",
                                "El mecánico dijo de que el carro estaba listo (incorrecto: dijo eso).",
                                "Pienso de que debemos cambiar la bujía (incorrecto: pienso eso).",
                                "Considero de que es muy tarde (incorrecto: considero eso)."
                            ),
                            correctOptionIndex = 0,
                            explanation = "'Estar seguro de algo' exige la preposición 'de'. Con verbos transitivos directos (decir, pensar) decir 'de que' es dequeísmo."
                        ),
                        InteractiveTaskData(
                            id = "task_esp8_transfer_anfibologia_informe",
                            conceptId = "cr_esp8_c_vicios_lenguaje",
                            title = "Eliminación de Anfibología (Doble Sentido Confuso)",
                            prompt = "La frase 'El perito revisó el vehículo del cliente con su asistente' es anfibológica (¿con el asistente de quién?). ¿Cómo se redacta con claridad jurídica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "'Acompañado por su propio asistente, el perito inspeccionó el vehículo perteneciente al cliente.'",
                                "'El perito lo revisó con el de él del cliente.'",
                                "'Se revisó el carro y el asistente también.'",
                                "'Revisaron el carro juntos los dos.'"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La anfibología se suprime especificando con claridad a qué sujeto pertenece el asistente para evitar litigios contractuales."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp8_u04",
            track = CurriculumTrack.ESPANOL_8,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "El Ensayo Argumentativo: Tesis, Antítesis y Conclusión",
            description = "Estructura del ensayo de ideas, conectores lógicos de contraste y fundamentación bibliográfica.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp8_c_ensayo_estructura",
                    conceptCode = "CR_ESP8_ENSAYO_ESTRUCTURA",
                    title = "Construcción de la Tesis y Argumentos de Soporte",
                    description = "Defensa de una postura crítica con rigor metodológico y sin falacias.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp8_tesis_definicion",
                            conceptId = "cr_esp8_c_ensayo_estructura",
                            title = "La Tesis en un Ensayo",
                            prompt = "¿Qué es la 'tesis' en un ensayo argumentativo?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "La proposición o postura central que el autor defiende y demuestra a lo largo de todo el texto",
                                "El índice de contenidos del libro",
                                "La bibliografía citada al final",
                                "Un resumen de una sola palabra"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La tesis es el eje vertebral argumentativo del ensayo; todos los párrafos subsiguientes aportan pruebas para sostenerla."
                        ),
                        InteractiveTaskData(
                            id = "task_esp8_transfer_ensayo_sostenibilidad",
                            conceptId = "cr_esp8_c_ensayo_estructura",
                            title = "Refutación de Contraargumento en Ensayo Técnico",
                            prompt = "En un ensayo a favor de la transición a transporte eléctrico en Costa Rica, ¿cuál es la refutación correcta al contraargumento 'la electricidad contamina igual al generarse'?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Demostrar con datos del ICE que en Costa Rica más del 98% de la electricidad proviene de fuentes renovables (agua, viento, geotermia, sol)",
                                "Decir que no importa que contamine",
                                "Negar que los autos eléctricos usen electricidad",
                                "Insultar a quien hace la objeción"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La refutación sólida desarma la premisa del oponente con evidencia empírica irrebatible de la matriz energética costarricense."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 8.º AÑO: CIENCIAS
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_cie8_sistema_circulatorio",
                            conceptId = "cr_cie8_c_celula",
                            title = "Función del Corazón y Vasos Sanguíneos",
                            prompt = "¿Qué vaso sanguíneo transporta sangre oxigenada a alta presión desde el ventrículo izquierdo hacia todo el cuerpo?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("La Arteria Aorta", "La Vena Cava", "La Arteria Pulmonar", "El capilar linfático"),
                            correctOptionIndex = 0,
                            explanation = "La arteria aorta es la principal arteria del cuerpo, distribuyendo la sangre oxigenada bombeada por el corazón."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cie8_u02",
            track = CurriculumTrack.CIENCIAS_8,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "El Sistema Locomotor, Ergonomía y Prevención de Lesiones",
            description = "Huesos, articulaciones, músculos esqueléticos y biomecánica del levantamiento de cargas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cie8_c_biomecanica",
                    conceptCode = "CR_CIE8_BIOMECANICA",
                    title = "Biomecánica y Salud Musculoesquelética",
                    description = "Palancas anatómicas de primer, segundo y tercer género en el cuerpo humano.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cie8_palanca_cuerpo",
                            conceptId = "cr_cie8_c_biomecanica",
                            title = "El Brazo Humano como Palanca de Tercer Género",
                            prompt = "En la flexión del antebrazo con el bíceps braquial, ¿qué tipo de palanca biomecánica opera?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Tercer género (la fuerza o potencia del bíceps se aplica entre el punto de apoyo en el codo y la resistencia en la mano)",
                                "Primer género (apoyo en el centro)",
                                "Segundo género (resistencia en el centro)",
                                "No hay palanca en el cuerpo"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El codo es el fulcro, el bíceps se inserta en el radio (potencia intermedia) y la mano sostiene la carga (resistencia)."
                        ),
                        InteractiveTaskData(
                            id = "task_cie8_transfer_levantamiento_cargas",
                            conceptId = "cr_cie8_c_biomecanica",
                            title = "Ergonomía en el Levantamiento de una Transmisión",
                            prompt = "Al levantar un objeto pesado del suelo (ej. una caja de cambios), ¿por qué se debe flexionar las rodillas manteniendo la espalda recta?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Para que la fuerza la ejerzan los músculos grandes de las piernas (cuádriceps y glúteos), evitando que las vértebras lumbares sufran hernias discales",
                                "Para que los brazos hagan toda la fuerza",
                                "Para no doblar los pantalones",
                                "No tiene importancia médica"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Levantar con la espalda curva multiplica por 10 la presión de cizallamiento sobre los discos lumbares L4-L5."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cie8_u03",
            track = CurriculumTrack.CIENCIAS_8,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Ondas, Luz, Sonido y Fenómenos Ondulatorios",
            description = "Ondas mecánicas y electromagnéticas, frecuencia (Hz), longitud de onda (λ), reflexión y refracción.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cie8_c_ondas",
                    conceptCode = "CR_CIE8_ONDAS",
                    title = "Propiedades de las Ondas y Acústica",
                    description = "Velocidad de propagación v = λ × f y el espectro electromagnético.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cie8_velocidad_onda",
                            conceptId = "cr_cie8_c_ondas",
                            title = "Relación Fundamental de Ondas",
                            prompt = "¿Cuál es la ecuación física que relaciona la velocidad de una onda (v) con su longitud de onda (λ) y su frecuencia (f)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("v = λ × f", "v = λ ÷ f", "v = f ÷ λ", "v = λ + f"),
                            correctOptionIndex = 0,
                            explanation = "La velocidad de propagación es el producto de la longitud de onda por la frecuencia: v = λ × f."
                        ),
                        InteractiveTaskData(
                            id = "task_cie8_transfer_proteccion_auditiva",
                            conceptId = "cr_cie8_c_ondas",
                            title = "Presión Sonora y Daño Coclear por Ruido",
                            prompt = "En un taller donde una amoladora angular emite 105 decibeles (dB), ¿por qué los tapones u orejeras de protección son obligatorios?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Los niveles superiores a 85 dB destruyen de forma irreversible las células ciliadas del caracol (cóclea), provocando sordera profesional (hipoacusia)",
                                "Para no escuchar al jefe",
                                "Porque el ruido congela las orejas",
                                "Para que no entre polvo al ojo"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La exposición continuada a más de 85 dB causa muerte celular coclear no regenerable."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cie8_u04",
            track = CurriculumTrack.CIENCIAS_8,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Electromagnetismo: Imanes, Campos Magnéticos e Inducción",
            description = "Polos magnéticos, campo magnético terrestre, electroimanes y Ley de Faraday.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cie8_c_electromagnetismo",
                    conceptCode = "CR_CIE8_ELECTROMAGNETISMO",
                    title = "Inducción Electromagnética y Máquinas Eléctricas",
                    description = "Un campo magnético variable en el tiempo induce una fuerza electromotriz en una bobina.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cie8_ley_faraday",
                            conceptId = "cr_cie8_c_electromagnetismo",
                            title = "Principio de la Inducción de Faraday",
                            prompt = "¿Qué ocurre cuando se mueve un imán rápidamente dentro de una bobina de alambre de cobre?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Se induce un voltaje y fluye una corriente eléctrica a través del alambre",
                                "El imán pierde todo su magnetismo para siempre",
                                "El cobre se derrite instantáneamente",
                                "No pasa absolutamente nada"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La variación del flujo magnético a través de las espiras induce una fuerza electromotriz (voltaje inducido)."
                        ),
                        InteractiveTaskData(
                            id = "task_cie8_transfer_alternador_vehiculo",
                            conceptId = "cr_cie8_c_electromagnetismo",
                            title = "Generación de Electricidad en el Alternador",
                            prompt = "¿Cómo transforma el alternador del motor la energía mecánica del cigüeñal en electricidad para recargar la batería?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Haciendo girar un rotor electromagnético dentro de un estator bobinado, induciendo corriente alterna trifásica que luego se rectifica a corriente directa",
                                "Por fricción química entre placas de carbón",
                                "Comprimiendo aire en un tanque",
                                "Quemando gasolina dentro de una dinamo"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El alternador aplica directamente la Ley de Faraday mediante rotores giratorios y diodos rectificadores."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 8.º AÑO: ESTUDIOS SOCIALES
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_soc8_transfer_moneda_cacao",
                            conceptId = "cr_soc8_c_colonial",
                            title = "El Cacao como Moneda de Intercambio en la Colonia",
                            prompt = "Ante la casi total ausencia de monedas metálicas de oro y plata en la Costa Rica colonial, ¿qué producto agrícola sirvió como patrón monetario oficial de cambio?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El grano de cacao de Matina",
                                "El banano",
                                "El petróleo",
                                "El café"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El cacao del valle de Matina funcionó durante generaciones como moneda de la tierra debido a la extrema escasez de circulante."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc8_u02",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_8,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Independencia de Centroamérica y Formación del Estado (1821-1848)",
            description = "El Acta de Independencia, el Pacto de Concordia, la Batalla de Ochomogo y la fundación de la República por José María Castro Madriz.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc8_c_independencia",
                    conceptCode = "CR_SOC8_INDEPENDENCIA",
                    title = "Del Pacto de Concordia a la República de 1848",
                    description = "Nacimiento constitucional de Costa Rica y separación definitiva de la República Federal.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc8_pacto_concordia",
                            conceptId = "cr_soc8_c_independencia",
                            title = "El Pacto Social Fundamental Interino (Pacto de Concordia)",
                            prompt = "¿Cuál es la trascendencia histórica del Pacto de Concordia de 1821 en Costa Rica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Fue la primera Constitución Política de Costa Rica, redactada por los propios costarricenses tras la independencia",
                                "Fue un tratado de guerra contra Panamá",
                                "Fue la venta del territorio a Inglaterra",
                                "Fue una ley para cobrar impuestos al café"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El Pacto de Concordia estableció las bases institucionales, libertades cívicas y la primera carta magna propia del país."
                        ),
                        InteractiveTaskData(
                            id = "task_soc8_transfer_castro_madriz",
                            conceptId = "cr_soc8_c_independencia",
                            title = "Fundación de la República de Costa Rica (1848)",
                            prompt = "¿Qué estadista declaró a Costa Rica como República soberana e independiente el 31 de agosto de 1848?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Dr. José María Castro Madriz (Primer Presidente de la República)",
                                "Juan Mora Fernández",
                                "Braulio Carrillo Colina",
                                "Tomás Guardia Gutiérrez"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Castro Madriz proclamó la República, consolidando la personería jurídica internacional y soberanía plena de Costa Rica."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc8_u03",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_8,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "La Campaña Nacional de 1856-1857 y la Soberanía Patria",
            description = "Gesta heroica contra los filibusteros de William Walker: Santa Rosa, Rivas, Juan Rafael Mora Porras y Juan Santamaría.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc8_c_campana_1856",
                    conceptCode = "CR_SOC8_CAMPANA_1856",
                    title = "Defensa de la Soberanía Centroamericana ante el Filibusterismo",
                    description = "Estrategia militar, Batalla de Santa Rosa (20 de marzo) y Batalla de Rivas (11 de abril de 1856).",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc8_proclama_mora",
                            conceptId = "cr_soc8_c_campana_1856",
                            title = "Liderazgo de Juan Rafael Mora Porras",
                            prompt = "¿Quién lideró al ejército expedicionario costarricense como Presidente y Comandante en Jefe durante la Campaña Nacional de 1856-1857?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Juan Rafael Mora Porras (junto al General José Joaquín Mora)",
                                "Braulio Carrillo",
                                "José Figueres Ferrer",
                                "Ricardo Jiménez"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Don 'Juanito' Mora organizó la movilización nacional y guió la expulsión de las tropas invasoras de William Walker."
                        ),
                        InteractiveTaskData(
                            id = "task_soc8_transfer_via_transito",
                            conceptId = "cr_soc8_c_campana_1856",
                            title = "Control Geopolítico de la Vía del Tránsito (Río San Juan)",
                            prompt = "¿Por qué la toma de los vapores filibusteros en la Vía del Tránsito en el Río San Juan fue la clave decisiva de la victoria costarricense?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Cortó el suministro de refuerzos, armas y dinero que Walker recibía desde los Estados Unidos por el Atlántico",
                                "Permitió a los soldados regresar a casa en barco",
                                "Porque allí había una mina de carbón",
                                "Para comerciar café con Nicaragua"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Al capturar la Vía del Tránsito, el ejército de Costa Rica asfixió logísticamente a los invasores, forzando su rendición en 1857."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc8_u04",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_8,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Demografía, Urbanización y Movilidad Espacial en el Siglo XXI",
            description = "Censo nacional, envejecimiento poblacional, pirámides demográficas y conurbación de la GAM.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc8_c_demografia_cr",
                    conceptCode = "CR_SOC8_DEMOGRAFIA_CR",
                    title = "Transición Demográfica y Planificación Urbana",
                    description = "Baja tasa de fecundidad, bono demográfico y retos de movilidad metropolitana.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc8_envejecimiento_poblacion",
                            conceptId = "cr_soc8_c_demografia_cr",
                            title = "Transición Demográfica en Costa Rica",
                            prompt = "¿Qué fenómeno demográfico experimenta Costa Rica actualmente respecto a su pirámide poblacional?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Un acelerado envejecimiento de la población debido a la caída en la tasa de natalidad y mayor esperanza de vida",
                                "Una explosión incontrolada de nacimientos infantiles",
                                "La desaparición de las personas mayores de 60 años",
                                "Una tasa de fecundidad de 5 hijos por mujer"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Costa Rica tiene la tasa de fecundidad más baja de América Latina (<1.3 hijos por mujer) y una esperanza de vida superior a 80 años."
                        ),
                        InteractiveTaskData(
                            id = "task_soc8_transfer_congestion_vial_gam",
                            conceptId = "cr_soc8_c_demografia_cr",
                            title = "Desafíos de la Conurbación y Congestión Vehicular",
                            prompt = "En la Gran Área Metropolitana (GAM), ¿qué solución estructural reduce el tiempo de viaje y la contaminación de forma sostenible?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Un sistema integrado de transporte público multimodal con tren eléctrico de pasajeros, sectorización de buses y ciclovías seguras",
                                "Permitir que cada ciudadano maneje dos autos a la vez",
                                "Quitar todas las aceras peatonales",
                                "Cerrar los colegios y hospitales"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El transporte masivo sobre rieles electrificado y la integración tarifaria son las únicas soluciones de ingeniería urbana a escala metropolitana."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 8.º AÑO: EDUCACIÓN CÍVICA
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_civ8_principios_ddhh",
                            conceptId = "cr_civ8_c_derechos_hum",
                            title = "Características de los Derechos Humanos",
                            prompt = "¿Cuáles son tres características intrínsecas esenciales de los Derechos Humanos?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Universales, inalienables e indivisibles",
                                "Temporales, revocables y costosos",
                                "Exclusivos para ciudadanos mayores de edad",
                                "Negociables en contratos privados"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Los derechos humanos pertenecen a toda persona por su dignidad intrínseca, no pueden enajenarse ni fragmentarse."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_civ8_u02",
            track = CurriculumTrack.CIVICA_8,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Seguridad Laboral y el Régimen de Riesgos del Trabajo (INS)",
            description = "Título IV del Código de Trabajo, póliza de riesgos laborales del INS y comités de salud ocupacional.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ8_c_salud_ocupacional",
                    conceptCode = "CR_CIV8_SALUD_OCUPACIONAL",
                    title = "Normativa de Salud Ocupacional y Seguro del INS",
                    description = "Obligaciones patronales de protección y comisiones de higiene y seguridad en el trabajo.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ8_poliza_riesgos_ins",
                            conceptId = "cr_civ8_c_salud_ocupacional",
                            title = "Obligatoriedad de la Póliza de Riesgos del Trabajo",
                            prompt = "En Costa Rica, ¿qué patrono está legalmente obligado a asegurar a sus trabajadores con la Póliza de Riesgos del Trabajo del INS?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Todo patrono sin excepción, ya sea de una gran empresa, un pequeño taller o una actividad temporal",
                                "Únicamente las empresas con más de 100 empleados",
                                "Solo el Estado",
                                "Ninguno, es voluntario"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El artículo 193 del Código de Trabajo estipula la obligatoriedad universal del seguro de riesgos del trabajo."
                        ),
                        InteractiveTaskData(
                            id = "task_civ8_transfer_accidente_laboral_taller",
                            conceptId = "cr_civ8_c_salud_ocupacional",
                            title = "Procedimiento ante Accidente Ocupacional",
                            prompt = "Si un mecánico sufre una quemadura química en los ojos trabajando, ¿cuál es el protocolo legal inmediato del taller?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Aplicar lavado de ojos de emergencia en la estación lavaojos por 15 minutos, trasladarlo a la clínica del INS y emitir el aviso de accidente en 48 horas",
                                "Enviarlo a su casa a descansar sin reportar",
                                "Despedirlo por descuidado",
                                "Esperar una semana a ver si se cura solo"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Se aplican primeros auxilios inmediatos y se traslada al centro médico del INS bajo la cobertura obligatoria de la póliza patronal."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_civ8_u03",
            track = CurriculumTrack.CIVICA_8,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "El Estado Social de Derecho y las Políticas Públicas Inclusivas",
            description = "Educación gratuita y costeada por el Estado (Art. 78 Constitución), CCSS, FODEFIR y transferencias sociales.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ8_c_estado_social",
                    conceptCode = "CR_CIV8_ESTADO_SOCIAL",
                    title = "Institucionalidad Social y Movilidad Económica",
                    description = "El papel redistributivo de la seguridad social y la educación técnica (INA/Colegios Técnicos).",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ8_art78_constitucion",
                            conceptId = "cr_civ8_c_estado_social",
                            title = "El Artículo 78 de la Constitución Política",
                            prompt = "¿Qué porcentaje mínimo del Producto Interno Bruto (PIB) manda la Constitución costarricense destinar a la educación estatal?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("El 8% del PIB", "El 2% del PIB", "El 20% del PIB", "El 4% del PIB"),
                            correctOptionIndex = 0,
                            explanation = "El Art. 78 de la Carta Magna establece que el gasto público en la educación estatal no será inferior al 8% del PIB anual."
                        ),
                        InteractiveTaskData(
                            id = "task_civ8_transfer_ina_formacion_tecnica",
                            conceptId = "cr_civ8_c_estado_social",
                            title = "El Instituto Nacional de Aprendizaje (INA)",
                            prompt = "¿Cuál es la misión social del INA y la educación técnica vocacional en la estructura productiva costarricense?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Capacitar gratuitamente a la fuerza laboral en carreras técnicas de alta demanda para generar empleo formal y elevar la productividad nacional",
                                "Cobrar altas colegiaturas privadas",
                                "Vender repuestos para vehículos",
                                "Regular las tarifas de taxis"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El INA financiado con aportes patronales forma técnicos calificados en mecánica, electrónica, software e industria."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_civ8_u04",
            track = CurriculumTrack.CIVICA_8,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Cultura de Paz, No Violencia y Desarme Histórico",
            description = "Abolición del Ejército en 1848, Premio Nobel de la Paz de 1987 y mediación internacional.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ8_c_cultura_paz",
                    conceptCode = "CR_CIV8_CULTURA_PAZ",
                    title = "Costa Rica y la Tradición Civilista Desarmada",
                    description = "Inversión de los recursos militares en salud, educación y parques nacionales.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ8_abolicion_ejercito_1948",
                            conceptId = "cr_civ8_c_cultura_paz",
                            title = "La Abolición del Ejército de Costa Rica",
                            prompt = "¿Qué fecha y quién protagonizó el acto simbólico de demoler las almenas del Cuartel Bellavista para abolir el ejército?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "El 1 de diciembre de 1948, por José Figueres Ferrer (convertido en Museo Nacional)",
                                "El 15 de setiembre de 1821, por Gabino Gaínza",
                                "El 11 de abril de 1856, por Juan Santamaría",
                                "El 25 de julio de 1950, por Otilio Ulate"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El 1 de diciembre de 1948 se abolió formalmente el ejército como institución permanente, hito ratificado en la Constitución de 1949."
                        ),
                        InteractiveTaskData(
                            id = "task_civ8_transfer_dividendo_paz",
                            conceptId = "cr_civ8_c_cultura_paz",
                            title = "El Dividendo de la Paz en Desarrollo Humano",
                            prompt = "En términos de políticas públicas, ¿cuál fue el mayor beneficio económico para Costa Rica de no poseer fuerzas armadas?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Poder destinar el presupuesto nacional a la construcción de hospitales de la CCSS, universidades públicas y escuelas en todo el país",
                                "Comprar armas más baratas a otros países",
                                "No tener policía de tránsito",
                                "Eliminar el pago de impuestos"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El ahorro militar sostenido durante más de 75 años permitió construir la red de seguridad social y educación más sólida de la región."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 8.º AÑO: INGLÉS
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_ing8_past_simple_regular",
                            conceptId = "cr_ing8_c_environment",
                            title = "Past Simple Tense in Maintenance Logs",
                            prompt = "Choose the correct past simple verb: 'Yesterday, the lead mechanic _______ all hydraulic lines for leaks.'",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("inspected", "inspecting", "inspects", "inspect"),
                            correctOptionIndex = 0,
                            explanation = "'Inspected' is the standard past simple form of the regular verb 'to inspect'."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing8_u02",
            track = CurriculumTrack.INGLES_8,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Technology, Gadgets & Digital Communication",
            description = "Comparative and superlative adjectives, technological devices, specs and diagnostics (A2+).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing8_c_technology",
                    conceptCode = "CR_ING8_TECHNOLOGY",
                    title = "Comparatives, Superlatives and Device Specifications",
                    description = "Comparing performance, speed, battery life and durability in English.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing8_comparatives_specs",
                            conceptId = "cr_ing8_c_technology",
                            title = "Comparative Adjectives in Engineering",
                            prompt = "Complete the technical comparison: 'Lithium-ion batteries are _______ than lead-acid batteries, but significantly _______.'",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "lighter / more expensive",
                                "lightest / expensivest",
                                "more light / expensiver",
                                "light / expensive"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Short adjective 'light' becomes 'lighter'; long adjective 'expensive' becomes 'more expensive'."
                        ),
                        InteractiveTaskData(
                            id = "task_ing8_transfer_diagnostic_specs",
                            conceptId = "cr_ing8_c_technology",
                            title = "Interpreting Diagnostic Scanner Readouts",
                            prompt = "An OBD scanner readout shows: 'FAULT: Fuel pressure is HIGHER THAN allowable threshold (450 kPa vs 350 kPa max)'. What does this indicate?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "La presión de combustible está por encima del límite máximo permitido",
                                "La presión de combustible está en cero",
                                "El tanque de gasolina está vacío",
                                "La batería se descargó"
                            ),
                            correctOptionIndex = 0,
                            explanation = "'Higher than allowable threshold' signifies that measured pressure exceeds the upper safety limit."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing8_u03",
            track = CurriculumTrack.INGLES_8,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Operating Manuals, Technical Sequences & Tool Handling",
            description = "Sequence connectors (first, then, next, after that, finally) and workshop tool handling (B1-).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing8_c_sequences",
                    conceptCode = "CR_ING8_SEQUENCES",
                    title = "Technical Sequences and Standard Operating Procedures (SOP)",
                    description = "Expressing sequential step-by-step instructions with chronological connectors.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing8_sequence_connectors",
                            conceptId = "cr_ing8_c_sequences",
                            title = "Correct Sequence Order",
                            prompt = "Which set of sequence transition words correctly links a 4-step procedure?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "First -> Next -> After that -> Finally",
                                "Finally -> First -> But -> Because",
                                "Then -> Yesterday -> Always -> Never",
                                "First -> First -> First -> Finally"
                            ),
                            correctOptionIndex = 0,
                            explanation = "'First', 'Next', 'After that', and 'Finally' form the standard chronological sequence structure."
                        ),
                        InteractiveTaskData(
                            id = "task_ing8_transfer_sop_tire_change",
                            conceptId = "cr_ing8_c_sequences",
                            title = "Reading a Wheel Nut Torquing SOP",
                            prompt = "Manual instruction: 'Always tighten wheel nuts in a criss-cross star pattern to prevent brake rotor warping.' What is the rationale?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Apretar en cruz o estrella para distribuir la tensión uniformemente y evitar deformar el disco de freno",
                                "Para terminar más rápido el trabajo",
                                "Para usar menos tuercas",
                                "Porque las llantas son cuadradas"
                            ),
                            correctOptionIndex = 0,
                            explanation = "A star pattern guarantees balanced clamp load distribution without inducing mechanical distortion."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing8_u04",
            track = CurriculumTrack.INGLES_8,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Workplace Safety Hazards, Warnings & First Aid English",
            description = "Caution, Warning, Danger alerts, imperative safety instructions and emergency reporting (B1-).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing8_c_safety_alerts",
                    conceptCode = "CR_ING8_SAFETY_ALERTS",
                    title = "International Workplace Safety Warnings",
                    description = "Distinguishing levels of hazard: CAUTION (minor), WARNING (severe), DANGER (lethal).",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing8_hazard_levels",
                            conceptId = "cr_ing8_c_safety_alerts",
                            title = "ANSI Z535 Hazard Classification Hierarchy",
                            prompt = "Which international warning signal word denotes the highest level of hazard indicating immediate risk of death or severe injury?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("DANGER (Peligro de muerte inminente)", "WARNING", "CAUTION", "NOTICE"),
                            correctOptionIndex = 0,
                            explanation = "'DANGER' is universally reserved for hazards that will result in death or serious irreversible injury if not avoided."
                        ),
                        InteractiveTaskData(
                            id = "task_ing8_transfer_first_aid_call",
                            conceptId = "cr_ing8_c_safety_alerts",
                            title = "Emergency 911 Dispatch in English",
                            prompt = "A bilingual dispatcher answers: '911 Emergency, what is your location and emergency?' What is the most precise technical response?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "'We have a chemical spill with one unconscious technician at EuroTaller Cartago, 200 meters west of the Basilica.'",
                                "'Hello, I am having a bad day today.'",
                                "'Someone call my mother please.'",
                                "'The car is completely broken.'"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Effective emergency calls immediately convey the exact physical address, nature of hazard, and clinical status of victims."
                        )
                    )
                )
            )
        )
    )
}
