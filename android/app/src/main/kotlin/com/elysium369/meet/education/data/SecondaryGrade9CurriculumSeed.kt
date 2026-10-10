package com.elysium369.meet.education.data

import com.elysium369.meet.education.domain.EpistemicTruthState

/**
 * Catálogo Curricular Completo para 9.º Año (III Ciclo / Tárcoles) — MEP Costa Rica 2026.
 * Incluye Matemática 9, Electricidad 9, Español 9, Ciencias 9, Estudios Sociales 9, Cívica 9 e Inglés 9.
 * Todas las materias con múltiples unidades, conceptos completos y tareas de transferencia PISA.
 */
object SecondaryGrade9CurriculumSeed {

    // ══════════════════════════════════════════════════════════════════════════
    // 9.º AÑO: MATEMÁTICA (TÁRCOLES)
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_mat9_transfer_diferencia_cuadrados",
                            conceptId = "cr_mat9_c_productos_notables",
                            title = "Factorización por Diferencia de Cuadrados",
                            prompt = "En el diseño de una brida circular de orificio concéntrico, el área de contacto es proporcional a (R² - r²). ¿Cómo se factoriza algebraicamente?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "(R - r)(R + r) (producto de binomios conjugados)",
                                "(R - r)²",
                                "2R - 2r",
                                "R² - 2Rr + r²"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La diferencia de cuadrados se factoriza exactamente como la suma por la diferencia de sus raíces: (R - r)(R + r)."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat9_u02",
            track = CurriculumTrack.MATEMATICA_9,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Ecuaciones Cuadráticas y la Fórmula General",
            description = "Resolución de ax² + bx + c = 0 mediante discriminante Δ = b² - 4ac y raíces reales.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat9_c_cuadraticas",
                    conceptCode = "CR_MAT9_CUADRATICAS",
                    title = "Ecuaciones Cuadráticas y Discriminante",
                    description = "Análisis del discriminante: Δ > 0 (dos soluciones), Δ = 0 (una solución), Δ < 0 (sin soluciones reales).",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat9_discriminante_calc",
                            conceptId = "cr_mat9_c_cuadraticas",
                            title = "Cálculo del Discriminante",
                            prompt = "Para la ecuación x² - 6x + 9 = 0, ¿cuál es el valor de Δ = b² - 4ac y qué indica sobre sus soluciones?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Δ = 0, posee una única solución real doble (x = 3)",
                                "Δ = 36, posee dos soluciones distintas",
                                "Δ = -36, no tiene soluciones reales",
                                "Δ = 9, posee infinitas soluciones"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Δ = (-6)² - 4(1)(9) = 36 - 36 = 0. Al ser cero, tiene una única raíz real: x = -b/(2a) = 6/2 = 3."
                        ),
                        InteractiveTaskData(
                            id = "task_mat9_transfer_trayectoria_proyectil",
                            conceptId = "cr_mat9_c_cuadraticas",
                            title = "Trayectoria Parabólica en Balística de Taller",
                            prompt = "La altura de un resorte eyectado sigue h(t) = -5t² + 20t. ¿En qué instantes de tiempo el resorte toca el suelo (h = 0)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "A los 0 segundos (lanzamiento) y a los 4 segundos (impacto con el suelo)",
                                "A los 2 segundos únicamente",
                                "A los 20 segundos",
                                "A los 5 segundos"
                            ),
                            correctOptionIndex = 0,
                            explanation = "-5t(t - 4) = 0 da dos soluciones: t = 0 s y t = 4 s."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat9_u03",
            track = CurriculumTrack.MATEMATICA_9,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Geometría: Teorema de Tales y Semejanza de Triángulos",
            description = "Criterios de semejanza (AA, LLL, LAL), proporcionalidad geométrica y cálculo de alturas inaccesibles.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat9_c_tales_semejanza",
                    conceptCode = "CR_MAT9_TALES_SEMEJANZA",
                    title = "El Teorema de Tales y Razones de Proporcionalidad",
                    description = "Segmentos determinados por rectas paralelas cortadas por transversales.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat9_tales_calculo",
                            conceptId = "cr_mat9_c_tales_semejanza",
                            title = "Cálculo de Proporcionalidad por Tales",
                            prompt = "En dos triángulos semejantes, los lados homólogos guardan la proporción a/a' = b/b'. Si a = 6, a' = 2 y b = 9, ¿cuánto mide b'?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("3 (porque 6/2 = 3 y 9/3 = 3)", "4.5", "18", "12"),
                            correctOptionIndex = 0,
                            explanation = "La razón de semejanza es 6/2 = 3. Por lo tanto, b' = 9/3 = 3."
                        ),
                        InteractiveTaskData(
                            id = "task_mat9_transfer_sombras_antena",
                            conceptId = "cr_mat9_c_tales_semejanza",
                            title = "Medición de Altura Inaccesible de una Torre de Taller",
                            prompt = "A la misma hora del día, un poste vertical de 2 m de altura proyecta una sombra de 1.5 m. Si la torre de telecomunicaciones del taller proyecta una sombra de 18 m, ¿cuál es su altura real?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "24 metros (Altura / Sombra = 2 / 1.5 = H / 18 -> H = 2 × 18 / 1.5 = 24 m)",
                                "36 metros",
                                "18 metros",
                                "13.5 metros"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Por semejanza de triángulos de sombra solar: H = (2 m × 18 m) / 1.5 m = 36 / 1.5 = 24 metros."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat9_u04",
            track = CurriculumTrack.MATEMATICA_9,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Estadística y Probabilidad: Dispersión y Diagramas de Cajas",
            description = "Rango intercuartílico (RIC), cuartiles (Q1, Q2, Q3), diagramas de caja y bigotes (Box-Plot).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat9_c_boxplot_dispersion",
                    conceptCode = "CR_MAT9_BOXPLOT_DISPERSION",
                    title = "Cuartiles y Variabilidad en Conjuntos de Datos",
                    description = "Cálculo de Q1 (25%), Q2 (mediana 50%) y Q3 (75%) e identificación de valores atípicos.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat9_cuartil_mediana",
                            conceptId = "cr_mat9_c_boxplot_dispersion",
                            title = "La Mediana y el Segundo Cuartil (Q2)",
                            prompt = "¿Qué porcentaje de los datos de una muestra ordenada queda por debajo del segundo cuartil Q2 (mediana)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Exactamente el 50%", "El 25%", "El 75%", "El 100%"),
                            correctOptionIndex = 0,
                            explanation = "Q2 divide la distribución exactamente en dos mitades del 50% de las observaciones cada una."
                        ),
                        InteractiveTaskData(
                            id = "task_mat9_transfer_tolerancia_fabricacion",
                            conceptId = "cr_mat9_c_boxplot_dispersion",
                            title = "Análisis de Dispersión en Control Estadístico de Procesos (SPC)",
                            prompt = "En la producción de pastillas de freno, la máquina A tiene un rango intercuartílico de grosor de 0.05 mm, mientras que la máquina B tiene un RIC de 0.40 mm. ¿Cuál máquina tiene mayor precisión y control de calidad?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "La máquina A, porque un menor rango intercuartílico evidencia mucha menor dispersión y mayor consistencia dimensional",
                                "La máquina B, porque los números grandes son mejores",
                                "Ambas son idénticas",
                                "Ninguna sirve"
                            ),
                            correctOptionIndex = 0,
                            explanation = "A menor dispersión intercuartílica, mayor homogeneidad y exactitud en las tolerancias de fabricación industrial."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 9.º AÑO: ELECTRICIDAD RESIDENCIAL
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_elec9_ley_potencia_joule",
                            conceptId = "cr_art_c_electricidad",
                            title = "Cálculo de Potencia Eléctrica (Ley de Watt)",
                            prompt = "Un reflector de taller de 120V consume una corriente de 2.5 A. ¿Cuál es su consumo de potencia eléctrica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("300 Watts (P = V × I = 120 × 2.5)", "48 Watts", "3000 Watts", "122.5 Watts"),
                            correctOptionIndex = 0,
                            explanation = "P = V × I = 120 V × 2.5 A = 300 Watts."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_el9_u02",
            track = CurriculumTrack.ELECTRICIDAD_9,
            unitNumber = 2,
            targetMonth = 9,
            monthName = "Septiembre",
            title = "Tableros de Distribución, Disyuntores y Protección GFCI",
            description = "Cálculo de cargas, interruptores termomagnéticos (breakers), protección de falla a tierra (GFCI) y calibres AWG.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_el9_c_tableros_gfci",
                    conceptCode = "CR_EL9_TABLEROS_GFCI",
                    title = "Protección Termomagnética y Diferencial",
                    description = "Dimensionamiento de disyuntores y calibración de interruptores de circuito por falla a tierra.",
                    targetMonth = 9,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_el9_disyuntor_termomagnetico",
                            conceptId = "cr_el9_c_tableros_gfci",
                            title = "Doble Función del Interruptor Termomagnético",
                            prompt = "¿Qué dos condiciones anómalas de peligro detecta y desconecta un disyuntor termomagnético en un tablero?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Sobrecarga térmica prolongada (lámina bimetálica) y cortocircuito instantáneo (bobina electromagnética)",
                                "Bajo voltaje y alta frecuencia",
                                "Falta de agua y vibración",
                                "Cambios de color en el cable"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La parte térmica protege contra sobrecargas por exceso de aparatos; la magnética abre en milisegundos ante cortocircuitos francos."
                        ),
                        InteractiveTaskData(
                            id = "task_el9_transfer_tomacorriente_gfci_humedad",
                            conceptId = "cr_el9_c_tableros_gfci",
                            title = "Protección GFCI en Bahías de Lavado y Zonas Húmedas",
                            prompt = "En un área de lavado de vehículos o taller con pisos húmedos, ¿por qué el Código Eléctrico exige tomacorrientes GFCI?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El GFCI compara la corriente de fase y neutro; si detecta una fuga a tierra de apenas 4-6 mA a través de una persona, corta la energía en 25 milisegundos para evitar electrocución fatal",
                                "Para que las hidrolavadoras laven más rápido",
                                "Para gastar menos agua",
                                "Porque son más baratos que los comunes"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El Ground Fault Circuit Interrupter salva vidas al detectar desbalances mínimos entre fase y neutro que fugan al cuerpo humano."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_el9_u03",
            track = CurriculumTrack.ELECTRICIDAD_9,
            unitNumber = 3,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Código Eléctrico de Costa Rica (NEC) y Sistema de Puesta a Tierra",
            description = "Electrodo de puesta a tierra (varilla Copperweld), conductor de puesta a tierra, código de colores y canalizaciones EMT/PVC.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_el9_c_puesta_a_tierra",
                    conceptCode = "CR_EL9_PUESTA_A_TIERRA",
                    title = "El Conductor de Protección y la Varilla de Tierra",
                    description = "Normativa oficial de colores: Fase (negro/rojo/azul), Neutro (blanco/gris) y Tierra (verde/desnudo).",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_el9_codigo_colores_cables",
                            conceptId = "cr_el9_c_puesta_a_tierra",
                            title = "Código Oficial de Colores de Conductores (NEC)",
                            prompt = "Según el Código Eléctrico de Costa Rica, ¿cuál es el color obligatorio para el conductor neutro y para el de tierra?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Neutro: Blanco o Gris claro; Tierra de protección: Verde o cobre desnudo",
                                "Neutro: Rojo; Tierra: Negro",
                                "Neutro: Amarillo; Tierra: Azul",
                                "Cualquier color sirve indistintamente"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El conductor neutro siempre debe ser blanco o gris; la tierra de protección física es exclusivamente verde o desnuda."
                        ),
                        InteractiveTaskData(
                            id = "task_el9_transfer_peligro_chasis_energizado",
                            conceptId = "cr_el9_c_puesta_a_tierra",
                            title = "Peligro de Falla en Compresor sin Puesta a Tierra",
                            prompt = "Si el devanado interno de un motor de compresor pierde aislamiento y toca la carcasa metálica sin conexión a tierra, ¿qué ocurre si un operador toca el equipo?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El chasis queda energizado a 120V/240V y la corriente circulará a tierra a través del cuerpo del operario, provocando choque eléctrico grave o paro cardíaco",
                                "El breaker se dispara automáticamente aunque no haya tierra",
                                "El compresor se apaga solo pacíficamente",
                                "El voltaje se transforma en calor inofensivo"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Sin conductor de tierra que derive la corriente de falla para disparar el disyuntor, la persona se convierte en el camino a tierra."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_el9_u04",
            track = CurriculumTrack.ELECTRICIDAD_9,
            unitNumber = 4,
            targetMonth = 11,
            monthName = "Noviembre",
            title = "Metrología Eléctrica: Uso del Multímetro Digital y Pinza Amperimétrica",
            description = "Medición de tensión (AC/DC), corriente, resistencia, continuidad, caída de tensión y diagnóstico de circuitos.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_el9_c_instrumentos_medicion",
                    conceptCode = "CR_EL9_INSTRUMENTOS_MEDICION",
                    title = "Medición Segura con Multímetro y Diagnóstico de Caída de Tensión",
                    description = "Categorías de seguridad CAT II, CAT III, CAT IV y técnicas de medición en paralelo vs serie.",
                    targetMonth = 11,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_el9_medicion_voltaje_paralelo",
                            conceptId = "cr_el9_c_instrumentos_medicion",
                            title = "Conexión del Voltímetro en el Circuito",
                            prompt = "¿Cómo se conectan las puntas de prueba de un multímetro digital para medir el voltaje disponible en un tomacorriente o batería?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "En paralelo directamente entre los dos puntos a medir (sin interrumpir el circuito)",
                                "En serie cortando el cable conductor",
                                "Tocando solo un cable y el piso",
                                "En escala de ohmios con el circuito encendido"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El voltímetro posee una impedancia interna altísima (>10 MΩ) y se conecta siempre en paralelo a la fuente o carga."
                        ),
                        InteractiveTaskData(
                            id = "task_el9_transfer_caida_tension_bornes",
                            conceptId = "cr_el9_c_instrumentos_medicion",
                            title = "Diagnóstico de Caída de Tensión por Resistencia Parásita",
                            prompt = "Al dar arranque a un motor, el voltímetro marca 12.6V en el poste de la batería, pero solo 8.2V en el terminal del motor de arranque. ¿Cuál es la causa raíz?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Una caída de tensión excesiva de 4.4V causada por corrosión o falso contacto en los bornes o cable de masa",
                                "La batería está perfecta y el motor de arranque también",
                                "El alternador genera demasiado voltaje",
                                "El cable tiene demasiados amperios guardados"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Cualquier resistencia parásita (sulfatación, flojedad) con cientos de amperios de arranque produce una severa caída de tensión (V = I × R)."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 9.º AÑO: ESPAÑOL
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_esp9_falacia_ad_hominem",
                            conceptId = "cr_esp9_c_ensayo",
                            title = "Detección de Falacia Ad Hominem",
                            prompt = "En un litigio técnico, la contraparte dice: 'Ese peritaje no vale nada porque el perito es muy joven'. ¿Qué falacia lógica comete?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Falacia Ad Hominem (atacar a la persona en lugar de refutar los datos técnicos de su dictamen)",
                                "Falacia de apelación a la autoridad",
                                "Argumento deductivo válido",
                                "Silogismo categórico"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La falacia ad hominem descalifica al emisor de manera personal en vez de analizar la validez de los argumentos y mediciones presentadas."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp9_u02",
            track = CurriculumTrack.ESPANOL_9,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "El Género Dramático y el Teatro Costarricense",
            description = "Estructura dramática: acto, escena, acotaciones, diálogo, monólogo y dramaturgia nacional (Daniel Gallegos, Alberto Cañas).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp9_c_teatro",
                    conceptCode = "CR_ESP9_TEATRO",
                    title = "Elementos del Texto Dramático y la Acotación Escénica",
                    description = "Diferenciar parlamentos de indicaciones técnicas del autor (acotaciones o didascalias).",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp9_acotaciones_teatro",
                            conceptId = "cr_esp9_c_teatro",
                            title = "Función de las Acotaciones en el Guión Teatral",
                            prompt = "En una obra dramática, ¿cuál es la función de los textos escritos entre paréntesis e itálicas conocidos como acotaciones?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Indicar a los actores y director los movimientos escénicos, gestos, tonos de voz e iluminación",
                                "Son palabras que los actores deben gritar al público",
                                "Son poesías para cantar",
                                "Indican el precio de la entrada"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Las acotaciones dirigen la puesta en escena, describiendo escenografía, entradas, salidas y ademanes de los personajes."
                        ),
                        InteractiveTaskData(
                            id = "task_esp9_transfer_analogia_manual_procedimiento",
                            conceptId = "cr_esp9_c_teatro",
                            title = "Analogía entre Acotaciones Teatrales y Procedimientos Operativos",
                            prompt = "Al igual que una acotación teatral dirige la acción sin formar parte del diálogo, ¿qué elemento en un manual de mantenimiento cumple la misma función reguladora?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Las notas de advertencia de seguridad, indicaciones de par de apriete (torque) y diagramas de ensamble",
                                "La portada del catálogo",
                                "El recibo de pago de la máquina",
                                "El logotipo de la fábrica"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Las notas técnicas prescriben la ejecución física rigurosa y las condiciones imperativas para que el proceso no falle."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp9_u03",
            track = CurriculumTrack.ESPANOL_9,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Sintaxis Avanzada: Oraciones Subordinadas Sustantivas y Adjetivas",
            description = "Nexo relacionante 'que', subordinadas adjetivas explicativas vs especificativas y signos de puntuación.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp9_c_subordinadas",
                    conceptCode = "CR_ESP9_SUBORDINADAS",
                    title = "Proposiciones Subordinadas Adjetivas y Puntuación",
                    description = "Uso de comas en oraciones adjetivas explicativas para evitar tergiversaciones legales.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp9_adjetiva_explicativa",
                            conceptId = "cr_esp9_c_subordinadas",
                            title = "Subordinada Adjetiva Explicativa entre Comas",
                            prompt = "En la frase: 'Los motores eléctricos, que fueron ensamblados en Alemania, superaron la prueba de vibración', ¿qué indica el uso de las comas?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Que TODOS los motores del lote fueron ensamblados en Alemania (explicativa para el conjunto total)",
                                "Que solo algunos pocos se ensamblaron allá",
                                "Que ningún motor sirvió",
                                "Un error ortográfico"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Las subordinadas explicativas van entre comas y predican sobre la totalidad del antecedente nominal."
                        ),
                        InteractiveTaskData(
                            id = "task_esp9_transfer_especificativa_contrato",
                            conceptId = "cr_esp9_c_subordinadas",
                            title = "Impacto Jurídico de la Subordinada Especificativa en Garantías",
                            prompt = "En una póliza: 'Las piezas que presenten desgaste por mal uso no tendrán cobertura'. Al no llevar comas (especificativa), ¿qué significa?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Únicamente quedan excluidas aquellas piezas específicas dañadas por mal uso; las demás piezas sí conservan su garantía",
                                "Se pierde la garantía de todo el automóvil completo",
                                "La garantía se duplica para todos",
                                "No significa nada vinculante"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La subordinada especificativa restringe el alcance exclusivamente al subconjunto que cumple la condición descrita."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp9_u04",
            track = CurriculumTrack.ESPANOL_9,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Redacción Técnica de Informes Periciales y Protocolos de Calidad",
            description = "Estructura del dictamen técnico: antecedentes, metodología, inspección ocular, pruebas de laboratorio, conclusiones y firma responsable.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp9_c_informe_pericial",
                    conceptCode = "CR_ESP9_INFORME_PERICIAL",
                    title = "Estructura y Rigor Metodológico del Informe Pericial",
                    description = "Redacción objetiva en tercera persona o pasiva refleja, sin juicios de valor subjetivos.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp9_objetividad_informe",
                            conceptId = "cr_esp9_c_informe_pericial",
                            title = "Registro Objetivo en Redacción Pericial",
                            prompt = "¿Cuál de las siguientes redacciones cumple con el estándar de objetividad científica para un informe pericial de arbitraje?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "'Se constató mediante manómetro calibrado que la presión hidráulica cayó a 15 psi, valor inferior a los 45 psi estipulados por el fabricante.'",
                                "'Me dio la impresión de que la máquina sonaba muy feo y el dueño me pareció sospechoso.'",
                                "'El vehículo estaba pésimo y daba lástima verlo.'",
                                "'Creo que el repuesto era de mala calidad porque era barato.'"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El lenguaje pericial se sustenta en mediciones empíricas con instrumentos trazables y parámetros oficiales de contraste."
                        ),
                        InteractiveTaskData(
                            id = "task_esp9_transfer_cadena_custodia",
                            conceptId = "cr_esp9_c_informe_pericial",
                            title = "Cadena de Custodia de Evidencias en Siniestros Viales",
                            prompt = "En un peritaje sobre la falla de frenos en un choque con víctimas, ¿por qué es indispensable registrar la cadena de custodia de la bomba de frenos retirada?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Para garantizar jurídicamente ante el tribunal que la pieza analizada en el laboratorio es idéntica a la del vehículo y no fue alterada ni sustituida",
                                "Para poder vender la pieza en el mercado negro",
                                "Para limpiarla y devolverla al dueño de inmediato",
                                "Es un trámite opcional sin validez legal"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La cadena de custodia documenta cada transferencia y análisis de la evidencia material, blindando el proceso contra nulidades procesales."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 9.º AÑO: CIENCIAS
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_cie9_tabla_periodica_grupos",
                            conceptId = "cr_cie9_c_atomo",
                            title = "Organización de la Tabla Periódica",
                            prompt = "¿Qué propiedad comparten los elementos químicos situados en una misma columna vertical (grupo o familia)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "El mismo número de electrones de valencia y propiedades químicas análogas",
                                "La misma masa atómica",
                                "El mismo número de protones",
                                "El mismo estado de agregación a cualquier temperatura"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Los elementos de un mismo grupo tienen idéntica configuración electrónica en su capa externa, determinando su reactividad química."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cie9_u02",
            track = CurriculumTrack.CIENCIAS_9,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Nomenclatura y Balanceo de Reacciones Químicas (Lavoisier)",
            description = "Ecuaciones químicas, reactivos, productos, coeficientes estequiométricos y balanceo por tanteo.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cie9_c_reacciones_balanceo",
                    conceptCode = "CR_CIE9_REACCIONES_BALANCEO",
                    title = "Ley de Conservación de la Materia en Reacciones",
                    description = "El número de átomos de cada elemento es exactamente idéntico en reactivos y productos.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cie9_balanceo_combustion",
                            conceptId = "cr_cie9_c_reacciones_balanceo",
                            title = "Balanceo de la Combustión del Metano (CH₄)",
                            prompt = "¿Cuáles son los coeficientes estequiométricos para balancear: CH₄ + __ O₂ -> CO₂ + __ H₂O?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("2 y 2 (CH₄ + 2 O₂ -> CO₂ + 2 H₂O)", "1 y 1", "3 y 2", "2 y 4"),
                            correctOptionIndex = 0,
                            explanation = "Hay 4 hidrógenos en CH₄ -> 2 H₂O (4 H y 2 O). Con CO₂ (2 O), suman 4 oxígenos en total -> 2 O₂."
                        ),
                        InteractiveTaskData(
                            id = "task_cie9_transfer_catalizador_emisiones",
                            conceptId = "cr_cie9_c_reacciones_balanceo",
                            title = "Química del Convertidor Catalítico de 3 Vías",
                            prompt = "En el catalizador automotriz con platino/rodio, ¿cómo se neutralizan los gases tóxicos CO y NOx antes de salir al aire?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Oxidando el monóxido de carbono a CO₂ inocuo y reduciendo los óxidos de nitrógeno a gas nitrógeno puro (N₂)",
                                "Congelando los gases en estado sólido",
                                "Transformando el escape en agua potable líquida",
                                "Absorbiendo el humo como una esponja que nunca se llena"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El catalizador acelera reacciones redox simultáneas: 2CO + O₂ -> 2CO₂ y 2NO -> N₂ + O₂."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cie9_u03",
            track = CurriculumTrack.CIENCIAS_9,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Genética Mendeliana, Cuadros de Punnett y Biotecnología",
            description = "Leyes de Gregor Mendel (segregación e independencia), genotipo, fenotipo y aplicaciones biotecnológicas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cie9_c_genetica_mendel",
                    conceptCode = "CR_CIE9_GENETICA_MENDEL",
                    title = "Herencia Genética y Proporciones Fenotípicas",
                    description = "Cálculo probabilístico de cruces monohíbridos y alelos dominantes/recesivos.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cie9_cruce_monohibrido",
                            conceptId = "cr_cie9_c_genetica_mendel",
                            title = "Proporción Fenotípica en Cruce Aa × Aa",
                            prompt = "Al cruzar dos individuos heterocigotos (Aa × Aa), ¿cuál es la proporción fenotípica mendeliana clásica de rasgo dominante vs recesivo?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("3 : 1 (75% dominante y 25% recesivo)", "1 : 1 (50% y 50%)", "4 : 0 (100% dominante)", "1 : 2 : 1"),
                            correctOptionIndex = 0,
                            explanation = "El cuadro de Punnett produce 1 AA, 2 Aa y 1 aa. Tanto AA como Aa expresan el fenotipo dominante (3 de 4 = 75%)."
                        ),
                        InteractiveTaskData(
                            id = "task_cie9_transfer_biocombustibles_genetica",
                            conceptId = "cr_cie9_c_genetica_mendel",
                            title = "Biotecnología y Microorganismos Modificados para Biodiésel",
                            prompt = "¿Cómo utiliza la biotecnología moderna la ingeniería genética para producir biocombustibles sostenibles?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Modificando microalgas y levaduras para optimizar la síntesis de lípidos que luego se transesterifican en biodiésel",
                                "Quemando bacterias directamente en el motor",
                                "Extrayendo petróleo crudo del mar",
                                "Mezclando tierra con gasolina"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Las cepas mejoradas de algas producen hasta un 60% de su biomasa seca en aceites vegetales aptos para biodiésel."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cie9_u04",
            track = CurriculumTrack.CIENCIAS_9,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Física de Fluidos: Principio de Pascal y Principio de Arquímedes",
            description = "Presión hidrostática (P = F/A), prensa hidráulica y fuerza de empuje boyante en fluidos.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cie9_c_fluidos_pascal",
                    conceptCode = "CR_CIE9_FLUIDOS_PASCAL",
                    title = "El Principio de Pascal y la Multiplicación de Fuerzas",
                    description = "La presión aplicada a un fluido incompresible confinado se transmite íntegramente en todas direcciones.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cie9_principio_pascal_formula",
                            conceptId = "cr_cie9_c_fluidos_pascal",
                            title = "Enunciado del Principio de Pascal",
                            prompt = "¿Qué postula el Principio de Pascal respecto a un líquido confinado en un circuito cerrado?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "La presión ejercida en un punto de un líquido incompresible cerrado se transmite con igual intensidad en todas las direcciones y sentidos",
                                "La presión disminuye a cero al alejarse del pistón",
                                "Los líquidos se comprimen a la mitad de su tamaño",
                                "La fuerza desaparece en las paredes del tubo"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La presión P = F₁/A₁ = F₂/A₂ es uniforme en todo el sistema hidráulico cerrado."
                        ),
                        InteractiveTaskData(
                            id = "task_cie9_transfer_elevador_hidraulico",
                            conceptId = "cr_cie9_c_fluidos_pascal",
                            title = "Cálculo de Fuerza en Elevador Hidráulico de Taller",
                            prompt = "Un elevador hidráulico tiene un pistón pequeño de área A₁ = 10 cm² y un pistón grande de área A₂ = 200 cm² (20 veces mayor). Si aplicamos una fuerza de 500 N en el pistón pequeño, ¿cuánta fuerza levanta el pistón grande?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "10.000 N (F₂ = F₁ × (A₂ / A₁) = 500 N × 20 = 10.000 N)",
                                "500 N (la misma fuerza)",
                                "25 N",
                                "2.500 N"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Al ser el área del pistón grande 20 veces mayor, la fuerza resultante se multiplica exactamente por 20: 500 × 20 = 10.000 N (capaz de levantar un auto de 1 tonelada)."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 9.º AÑO: ESTUDIOS SOCIALES
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_soc9_transfer_ccss_seguro_social",
                            conceptId = "cr_soc9_c_reformas43",
                            title = "Creación de la Caja Costarricense de Seguro Social (CCSS)",
                            prompt = "Promulgada en 1941, ¿cuál fue el propósito fundamental de la creación de la CCSS en Costa Rica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Garantizar la cobertura universal de salud, maternidad e invalidez, vejez y muerte mediante el principio tripartito (trabajador, patrono y Estado)",
                                "Cobrar impuestos a los medicamentos",
                                "Construir ferrocarriles exclusivamente",
                                "Privatizar la atención médica hospitalaria"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La CCSS institucionalizó la solidaridad social universal, transformando las condiciones sanitarias y de vida de Costa Rica."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc9_u02",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_9,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "La Guerra Civil de 1948 y la Constitución Política de 1949",
            description = "El Ejército de Liberación Nacional, José Figueres Ferrer, el Pacto Ulate-Figueres y la nueva Carta Magna.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc9_c_constitucion1949",
                    conceptCode = "CR_SOC9_CONSTITUCION1949",
                    title = "El Hito Constitucional de 1949 y Nuevas Instituciones",
                    description = "Voto femenino, creación del TSE, nacionalización bancaria y abolición del ejército.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc9_voto_femenino",
                            conceptId = "cr_soc9_c_constitucion1949",
                            title = "El Sufragio Universal y el Voto Femenino en 1949",
                            prompt = "¿Qué derecho político fundamental consagró por primera vez en la historia nacional la Constitución Política de 1949?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "El derecho de las mujeres a votar y ser electas en cargos públicos con plena igualdad jurídica",
                                "El derecho de reelección presidencial indefinida",
                                "La pena de muerte",
                                "La prohibición de la educación secundaria"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La Carta Magna de 1949 reconoció la ciudadanía plena y el derecho universal al sufragio femenino."
                        ),
                        InteractiveTaskData(
                            id = "task_soc9_transfer_nacionalizacion_bancaria",
                            conceptId = "cr_soc9_c_constitucion1949",
                            title = "Nacionalización Bancaria y Crédito al Desarrollo",
                            prompt = "En 1948 la Junta Fundadora nacionalizó la banca privada. ¿Qué impacto tuvo para los pequeños productores agrícolas y talleres?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Democratizó el acceso al crédito barato y productivo para campesinos, artesanos y pequeñas empresas en todo el país",
                                "Cerró todos los bancos y eliminó el dinero en colones",
                                "Obligó a pagar intereses del 100% mensual",
                                "Exclusividad del crédito para empresas extranjeras"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Los bancos del Estado orientaron los depósitos de los ahorrantes hacia créditos de fomento agrícola e industrial."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc9_u03",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_9,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "El Estado Benefactor y el Modelo de Sustitución de Importaciones (1950-1980)",
            description = "El Mercado Común Centroamericano (MCCA), creación del ICE, RECOPE, CNP e industrialización liviana.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc9_c_modelo_isi",
                    conceptCode = "CR_SOC9_MODELO_ISI",
                    title = "Electrificación Nacional con el ICE y Crecimiento Industrial",
                    description = "Desarrollo de la infraestructura eléctrica, telecomunicaciones y clase media costarricense.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc9_creacion_ice",
                            conceptId = "cr_soc9_c_modelo_isi",
                            title = "Fundación del Instituto Costarricense de Electricidad (ICE)",
                            prompt = "En 1949 se fundó el ICE. ¿Cuál fue su cometido estratégico nacional para el desarrollo humano y productivo?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Electrificar todo el territorio costarricense aprovechando los recursos hídricos renovables y modernizar las telecomunicaciones",
                                "Importar petróleo y carbón de otros países",
                                "Cobrar peajes en las carreteras",
                                "Fabricar automóviles a gasolina"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El ICE llevó electricidad limpia al 99.4% de los hogares de Costa Rica, impulsando la agroindustria y talleres."
                        ),
                        InteractiveTaskData(
                            id = "task_soc9_transfer_crisis_petroleo_1980",
                            conceptId = "cr_soc9_c_modelo_isi",
                            title = "La Crisis Económica de los Años 80 en Costa Rica",
                            prompt = "A inicios de los años 80 Costa Rica sufrió una severa crisis económica. ¿Cuáles factores internacionales y locales la desencadenaron?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El alza mundial en los precios del petróleo, la caída estrepitosa del precio del café y el excesivo endeudamiento externo en dólares",
                                "Un terremoto en Europa",
                                "La falta de lluvia durante 50 años",
                                "El cierre de las escuelas públicas"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El shock petrolero y la subida de tasas de interés internacionales colapsaron las reservas monetarias y devaluaron el colón fuertemente."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc9_u04",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_9,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Costa Rica en la Globalización: Zonas Francas, Dispositivos Médicos y Turismo",
            description = "Tratados de Libre Comercio (TLC), transición de economía agrícola a exportación de alta tecnología y servicios.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc9_c_globalizacion_zonas_francas",
                    conceptCode = "CR_SOC9_GLOBALIZACION_ZONAS_FRANCAS",
                    title = "Atracción de Inversión Extranjera Directa (CINDE / PROCOMER)",
                    description = "El clúster de ciencias de la vida, dispositivos médicos y desarrollo de software.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc9_dispositivos_medicos_exportacion",
                            conceptId = "cr_soc9_c_globalizacion_zonas_francas",
                            title = "Principal Producto de Exportación de Costa Rica Actual",
                            prompt = "¿Cuál es hoy en día el principal rubro de exportación de bienes de alto valor agregado de Costa Rica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Dispositivos médicos y equipo de precisión de alta tecnología",
                                "El café en grano exclusivamente",
                                "El banano fresco",
                                "El azúcar y el tabaco"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Los dispositivos médicos (válvulas, catéteres, prótesis) representan más del 40% de las exportaciones totales de bienes del país."
                        ),
                        InteractiveTaskData(
                            id = "task_soc9_transfer_formacion_tecnica_empleo",
                            conceptId = "cr_soc9_c_globalizacion_zonas_francas",
                            title = "Sinergia entre Educación Técnica y Empleo en Zonas Francas",
                            prompt = "¿Por qué las empresas multinacionales de tecnología y manufactura avanzada eligen operar en Costa Rica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Por la estabilidad democrática, seguridad jurídica y el talento humano técnico altamente calificado y bilingüe egresado de colegios técnicos y universidades públicas",
                                "Porque los salarios son los más bajos del mundo",
                                "Porque no hay leyes laborales",
                                "Porque el clima nunca cambia"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El capital humano calificado formado en el sistema educativo técnico estatal es la principal ventaja competitiva del país."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 9.º AÑO: EDUCACIÓN CÍVICA
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_civ9_concejo_municipal_funciones",
                            conceptId = "cr_civ9_c_municipal",
                            title = "El Concejo Municipal y el Alcalde",
                            prompt = "¿Cuál es la diferencia de roles entre el Concejo Municipal y el Alcalde en un gobierno local costarricense?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "El Concejo Municipal es el órgano deliberativo y normativo (regidores), mientras que el Alcalde es el administrador ejecutivo que ejecuta los acuerdos",
                                "El Alcalde hace las leyes y el Concejo las aprueba",
                                "El Concejo solo se reúne una vez al año",
                                "Son el mismo cargo con diferente nombre"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El Concejo Municipal equivale al poder legislativo del cantón y la Alcaldía al poder ejecutivo ejecutor de proyectos."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_civ9_u02",
            track = CurriculumTrack.CIVICA_9,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Mecanismos de Participación Ciudadana Directa: Plebiscito, Referéndum y Cabildo",
            description = "Democracia semidirecta en Costa Rica: Ley de Referéndum (Ley 8492) y consultas populares cantonales.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ9_c_participacion_directa",
                    conceptCode = "CR_CIV9_PARTICIPACION_DIRECTA",
                    title = "Consultas Populares y Soberanía Ejercida por el Pueblo",
                    description = "Aprobación o derogación de leyes cantonales y revocatoria de mandato de alcaldes.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ9_plebiscito_revocatoria",
                            conceptId = "cr_civ9_c_participacion_directa",
                            title = "Plebiscito de Revocatoria de Mandato",
                            prompt = "¿Qué mecanismo democrático cantonal permite a los vecinos de un cantón destituir legalmente a un alcalde antes de finalizar su período?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "El plebiscito revocatorio de mandato regulado en el Código Municipal",
                                "Una huelga general",
                                "Una orden de la policía",
                                "No se puede destituir nunca"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El Código Municipal contempla el plebiscito de revocatoria convocado por el Concejo Municipal ante incumplimiento grave."
                        ),
                        InteractiveTaskData(
                            id = "task_civ9_transfer_cabildo_abierto_comunidad",
                            conceptId = "cr_civ9_c_participacion_directa",
                            title = "El Cabildo Abierto para Planes Reguladores",
                            prompt = "Cuando una municipalidad va a modificar su Plan Regulador Territorial (zonificación urbana y de talleres), ¿qué derecho cívico tienen los vecinos?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Participar en la audiencia pública y cabildo abierto para formular objeciones técnicas fundamentadas antes de la aprobación",
                                "Aceptar las decisiones sin poder opinar",
                                "Vender sus casas obligatoriamente",
                                "Cerrar las calles indefinidamente"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La legislación urbana exige consulta pública y audiencia abierta vinculante a la ciudadanía para el ordenamiento del cantón."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_civ9_u03",
            track = CurriculumTrack.CIVICA_9,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Transparencia, Rendición de Cuentas y Control de la Corrupción",
            description = "Contraloría General de la República (CGR), Defensoría de los Habitantes, SICOP y compras públicas transparentes.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ9_c_transparencia_cgr",
                    conceptCode = "CR_CIV9_TRANSPARENCIA_CGR",
                    title = "Fiscalización de Fondos Públicos y Plataforma SICOP",
                    description = "Control de legalidad presupuestaria, licitaciones electrónicas y acceso a la información pública.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ9_rol_contraloria",
                            conceptId = "cr_civ9_c_transparencia_cgr",
                            title = "Misión de la Contraloría General de la República (CGR)",
                            prompt = "¿Cuál es el mandato constitucional exclusivo de la Contraloría General de la República (CGR)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Fiscalizar el uso correcto y legal de los fondos públicos de todas las instituciones del Estado y municipalidades",
                                "Juzgar delitos en materia penal de tránsito",
                                "Organizar los comicios electorales",
                                "Nombrar a los diputados de la Asamblea"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La CGR es el órgano auxiliar de la Asamblea Legislativa que vigila y audita la hacienda pública de la nación."
                        ),
                        InteractiveTaskData(
                            id = "task_civ9_transfer_licitacion_sicop_taller",
                            conceptId = "cr_civ9_c_transparencia_cgr",
                            title = "Participación Transparente en Compras Públicas (SICOP)",
                            prompt = "Si un taller o empresa técnica desea ofrecer servicios de mantenimiento a la flota de patrullas o ambulancias del Estado, ¿dónde debe competir?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "En el Sistema Integrado de Compras Públicas (SICOP), donde todas las ofertas son públicas, trazables y auditables",
                                "Por acuerdos secretos debajo de la mesa",
                                "Pagando un porcentaje a los funcionarios",
                                "Enviando una carta confidencial sin concurso"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La Ley de Contratación Pública exige que todas las compras estatales se realicen por SICOP para garantizar libre competencia e integridad."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_civ9_u04",
            track = CurriculumTrack.CIVICA_9,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Liderazgo Democrático, Voluntariado y Gestión Comunal",
            description = "Habilidades blandas, comités cantonales de deportes y recreación (CCDR), brigadas ambientales y cruzada contra la exclusión.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ9_c_liderazgo_comunal",
                    conceptCode = "CR_CIV9_LIDERAZGO_COMUNAL",
                    title = "El Líder Democrático y la Cohesión Social",
                    description = "Diferencia entre líder autoritario, permisivo (laissez-faire) y líder democrático participativo.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ9_estilos_liderazgo",
                            conceptId = "cr_civ9_c_liderazgo_comunal",
                            title = "Características del Liderazgo Democrático",
                            prompt = "¿Qué conducta distingue a un líder verdaderamente democrático en una organización o equipo técnico?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Fomenta la participación activa de todos, escucha opiniones divergentes, construye consensos y delega con responsabilidad",
                                "Impone órdenes sin permitir preguntas",
                                "Deja que cada quien haga lo que quiera sin coordinación ni rumbo",
                                "Se apropia del mérito del trabajo de los demás"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El liderazgo democrático potencia la inteligencia colectiva, la corresponsabilidad y la transparencia en las decisiones."
                        ),
                        InteractiveTaskData(
                            id = "task_civ9_transfer_proyecto_comunitario_reciclaje",
                            conceptId = "cr_civ9_c_liderazgo_comunal",
                            title = "Gestión de un Proyecto Cantonal de Recolección de Aceite",
                            prompt = "Un grupo de jóvenes líderes lidera un proyecto comunal para recolectar aceite de cocina y automotriz usado en su cantón. ¿Cuál es el primer paso metodológico?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Realizar un diagnóstico comunitario de necesidades, articular alianzas con la Municipalidad y centros de valorización autorizados, y capacitar a la población",
                                "Comenzar a recolectar barriles sin tener dónde almacenarlos",
                                "Tirar el aceite en el parque",
                                "Cobrar multas a los vecinos por cuenta propia"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Todo proyecto cívico exitoso parte del diagnóstico empírico, la legalidad formal y la alianza interinstitucional con la comunidad."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 9.º AÑO: INGLÉS
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_ing9_modal_distinction_should_must",
                            conceptId = "cr_ing9_c_workplace",
                            title = "Modal Verbs: 'Should' (Recommendation) vs 'Must' (Obligation)",
                            prompt = "In technical writing, what is the exact semantic difference between 'You should calibrate' and 'You must calibrate'?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "'Should' expresses a best-practice advice or recommendation; 'Must' expresses a mandatory binding legal/safety requirement",
                                "They mean exactly the same thing",
                                "'Must' is polite and optional; 'Should' is forbidden",
                                "'Should' is past tense and 'Must' is future tense"
                            ),
                            correctOptionIndex = 0,
                            explanation = "'Must' indicates an unavoidable contractual or safety requirement; 'should' indicates a strong engineering recommendation."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing9_u02",
            track = CurriculumTrack.INGLES_9,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Technical Datasheets, Schematics & Specifications Reading",
            description = "Interpreting voltage, wattage, tolerances, wiring diagrams, and abbreviations (B1).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing9_c_datasheets",
                    conceptCode = "CR_ING9_DATASHEETS",
                    title = "Component Specifications and Wiring Schematics",
                    description = "Reading international engineering datasheets without translation errors.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing9_spec_abbreviations",
                            conceptId = "cr_ing9_c_datasheets",
                            title = "Standard Engineering Abbreviations",
                            prompt = "On an automotive electrical datasheet, what do 'GND', 'VCC', and 'RPM' stand for?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Ground (Chassis Ground) / Voltage Common Collector (Power Supply) / Revolutions Per Minute",
                                "General / Velocity / Radiator",
                                "Gas / Volume / Resistance",
                                "Gear / Valve / Relay"
                            ),
                            correctOptionIndex = 0,
                            explanation = "GND indicates electrical reference ground (0V); VCC indicates supply rail; RPM indicates angular velocity."
                        ),
                        InteractiveTaskData(
                            id = "task_ing9_transfer_troubleshooting_flowchart",
                            conceptId = "cr_ing9_c_datasheets",
                            title = "Following a Troubleshooting Flowchart in English",
                            prompt = "A factory manual decision diamond asks: 'Is supply voltage ≥ 11.5 VDC?'. If NO, the arrow points to: 'Recharge or replace battery before probing CAN lines'. What must the technician do?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Recargar o sustituir la batería antes de medir las líneas de comunicación CAN",
                                "Desarmar el motor completo de inmediato",
                                "Cortar los cables de la computadora",
                                "Ignorar el voltaje e inspeccionar los frenos"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Low battery voltage corrupts CAN bus communication frames; charging or replacing the battery is the mandatory prerequisite step."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing9_u03",
            track = CurriculumTrack.INGLES_9,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "First Conditional in Diagnostic Logic: Cause and Effect",
            description = "If + Present Simple, will + base form in technical predictive troubleshooting (B1).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing9_c_first_conditional",
                    conceptCode = "CR_ING9_FIRST_CONDITIONAL",
                    title = "First Conditional in Troubleshooting Scenarios",
                    description = "Formulating real, likely conditions and their consequences in engineering.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing9_first_conditional_structure",
                            conceptId = "cr_ing9_c_first_conditional",
                            title = "Grammar: First Conditional Structure",
                            prompt = "Complete the sentence correctly: 'If the coolant temperature _______ 105 °C, the radiator fan _______ automatically.'",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "exceeds / will activate",
                                "exceeded / will activate",
                                "exceed / activated",
                                "will exceed / activates"
                            ),
                            correctOptionIndex = 0,
                            explanation = "The First Conditional requires 'If' + Present Simple ('exceeds') in the condition clause and 'will' + base verb ('will activate') in the result clause."
                        ),
                        InteractiveTaskData(
                            id = "task_ing9_transfer_preventive_cause_effect",
                            conceptId = "cr_ing9_c_first_conditional",
                            title = "Explaining Risk to an International Fleet Manager",
                            prompt = "How should a bilingual service advisor professionally explain the consequence of postponing a timing belt replacement?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "'If you do not replace the timing belt at 100,000 km, it will snap and cause catastrophic engine valve collision.'",
                                "'Maybe belt is bad, you buy another car.'",
                                "'Belt is rubber, rubber is black.'",
                                "'Yesterday the belt was working fine.'"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Using clear First Conditional ('If you do not replace... it will snap and cause...') conveys risk with professional clarity."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing9_u04",
            track = CurriculumTrack.INGLES_9,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Job Interviews, Technical CVs & Customer Service in English",
            description = "Describing past experience (Present Perfect), soft skills, technical certifications and professional customer service (B1).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing9_c_interview_customer_service",
                    conceptCode = "CR_ING9_INTERVIEW_CUSTOMER_SERVICE",
                    title = "Professional Job Interview and Client Communication",
                    description = "Highlighting technical competencies, certifications and resolving customer complaints with courtesy.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing9_present_perfect_experience",
                            conceptId = "cr_ing9_c_interview_customer_service",
                            title = "Present Perfect for Career Experience",
                            prompt = "In a job interview, how do you express your experience with diagnostic tools using the Present Perfect tense?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "'I have operated digital oscilloscopes and diagnostic scanners for over three years.'",
                                "'I am operating oscilloscopes tomorrow.'",
                                "'I operate oscilloscopes yesterday.'",
                                "'I had been oscilloscopes.'"
                            ),
                            correctOptionIndex = 0,
                            explanation = "'I have operated...' (have/has + past participle) is the canonical grammatical structure to express life and career experience."
                        ),
                        InteractiveTaskData(
                            id = "task_ing9_transfer_customer_deescalation",
                            conceptId = "cr_ing9_c_interview_customer_service",
                            title = "De-escalating an Upset International Client",
                            prompt = "An English-speaking customer is angry because their delivery was delayed by two hours. Which phrase de-escalates the tension with top professionalism?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "'I sincerely apologize for the delay. We encountered an unforeseen calibration check; let me explain the report and waive the diagnostic fee for the inconvenience.'",
                                "'Calm down, it is not my problem.'",
                                "'Go complain to the manager if you want.'",
                                "'You should wait outside because I am busy.'"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Acknowledging feelings, explaining technical necessity with empathy, and offering fair compensation provides world-class customer service."
                        )
                    )
                )
            )
        )
    )
}
