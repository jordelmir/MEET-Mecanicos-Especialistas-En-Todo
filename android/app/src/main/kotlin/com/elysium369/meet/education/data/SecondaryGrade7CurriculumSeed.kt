package com.elysium369.meet.education.data

import com.elysium369.meet.education.domain.EpistemicTruthState

/**
 * Catálogo Curricular Completo para 7.º Año (III Ciclo / Zapandí) — MEP Costa Rica 2026.
 * Incluye Matemática 7, Español 7, Ciencias 7, Estudios Sociales 7, Cívica 7, Inglés 7 y Ciencias III Ciclo.
 * Todas las materias integran rigor académico oficial MEP, contexto vocacional/STEM y tareas de transferencia PISA.
 */
object SecondaryGrade7CurriculumSeed {

    // ══════════════════════════════════════════════════════════════════════════
    // 7.º AÑO: MATEMÁTICA (ZAPANDÍ)
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_mat7_transfer_balance_termico",
                            conceptId = "cr_mat7_c_enteros",
                            title = "Balance Térmico en Sistema Criogénico",
                            prompt = "Una cámara de prueba baja a -15 °C. Luego sube 8 °C y finalmente desciende otros 5 °C. ¿Cuál es su temperatura final?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("-12 °C (-15 + 8 - 5 = -12)", "-2 °C", "-28 °C", "-18 °C"),
                            correctOptionIndex = 0,
                            explanation = "-15 + 8 = -7; -7 - 5 = -12 °C."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat7_u02",
            track = CurriculumTrack.MATEMATICA_7,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Potenciación y Radicación en Números Enteros",
            description = "Propiedades de las potencias, base negativa, exponente par/impar y raíces cuadradas exactas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat7_c_potencias",
                    conceptCode = "CR_MAT7_POTENCIAS",
                    title = "Propiedades de Potencias con Base Entera",
                    description = "a^m × a^n = a^(m+n), potencia de una potencia y signo según la paridad.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat7_base_negativa",
                            conceptId = "cr_mat7_c_potencias",
                            title = "Potencia con Base Negativa",
                            prompt = "¿Cuál es el valor de (-3)³ y de (-3)⁴?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("-27 y +81", "+27 y -81", "-9 y +12", "-27 y -81"),
                            correctOptionIndex = 0,
                            explanation = "(-3)³ = (-3)(-3)(-3) = -27 (impar es negativo). (-3)⁴ = +81 (par es positivo)."
                        ),
                        InteractiveTaskData(
                            id = "task_mat7_transfer_notacion_cientifica",
                            conceptId = "cr_mat7_c_potencias",
                            title = "Cálculo de Potencia Disipada",
                            prompt = "En un circuito eléctrico la disipación es P = I² × R. Si la corriente es I = 5 A y la resistencia es R = 4 Ω, ¿cuál es la potencia en Watts?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("100 Watts (5² × 4 = 25 × 4)", "40 Watts", "20 Watts", "200 Watts"),
                            correctOptionIndex = 0,
                            explanation = "5² = 25. Multiplicado por 4 Ω: 25 × 4 = 100 Watts."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat7_u03",
            track = CurriculumTrack.MATEMATICA_7,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Geometría Euclidiana: Rectas Paralelas, Perpendiculares y Triángulos",
            description = "Ángulos correspondientes, alternos internos, clasificación y propiedades de triángulos.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat7_c_rectas_angulos",
                    conceptCode = "CR_MAT7_RECTAS_ANGULOS",
                    title = "Ángulos Formados por Rectas Cortadas por una Transversal",
                    description = "Congruencia de ángulos alternos internos y correspondientes entre paralelas.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat7_angulos_paralelas",
                            conceptId = "cr_mat7_c_rectas_angulos",
                            title = "Ángulos Alternos Internos",
                            prompt = "Dos rectas paralelas son cortadas por una transversal. Si un ángulo alterno interno mide 65°, ¿cuánto mide el otro ángulo alterno interno?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("65° (son congruentes)", "115°", "90°", "180°"),
                            correctOptionIndex = 0,
                            explanation = "Los ángulos alternos internos entre rectas paralelas son siempre congruentes (iguales)."
                        ),
                        InteractiveTaskData(
                            id = "task_mat7_transfer_alineacion_ejes",
                            conceptId = "cr_mat7_c_rectas_angulos",
                            title = "Paralelismo en los Ejes de un Chasis",
                            prompt = "Para que un vehículo ruede sin desgastar los neumáticos de manera despareja, los dos ejes deben estar estrictamente paralelos entre sí. ¿Qué ángulo relativo forman sus líneas axiales?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "0° (paralelos, distancia constante en todos sus puntos)",
                                "90° (perpendiculares)",
                                "45°",
                                "180° formando una sola línea"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Las líneas paralelas nunca se intersectan y mantienen un ángulo relativo de 0°."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat7_u04",
            track = CurriculumTrack.MATEMATICA_7,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Estadística y Probabilidad: Distribución de Frecuencias",
            description = "Tablas de frecuencia absoluta, relativa, porcentual y cálculo de media, moda y mediana.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat7_c_estadistica_zapandi",
                    conceptCode = "CR_MAT7_ESTADISTICA_ZAPANDI",
                    title = "Tablas de Frecuencias y Medidas de Posición",
                    description = "Construcción e interpretación de frecuencias relativas y modas.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat7_moda_frecuencia",
                            conceptId = "cr_mat7_c_estadistica_zapandi",
                            title = "Identificación de la Moda",
                            prompt = "En una muestra de diámetros de pernos: [10, 12, 10, 14, 10, 12, 16], ¿cuál es la moda?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("10 (aparece 3 veces)", "12", "14", "84"),
                            correctOptionIndex = 0,
                            explanation = "La moda es el valor que se repite con mayor frecuencia absoluta."
                        ),
                        InteractiveTaskData(
                            id = "task_mat7_transfer_control_fallas",
                            conceptId = "cr_mat7_c_estadistica_zapandi",
                            title = "Diagrama de Frecuencia de Fallos de Taller",
                            prompt = "En 50 diagnósticos: 25 fallos fueron de batería, 15 de alternador y 10 de bujías. ¿Cuál es el porcentaje relativo del fallo de batería?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("50% (25 ÷ 50 × 100%)", "25%", "40%", "30%"),
                            correctOptionIndex = 0,
                            explanation = "25 de 50 equivale a 25/50 = 0.50 = 50% de las incidencias."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 7.º AÑO: ESPAÑOL
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_esp7_transfer_resumen_tecnico",
                            conceptId = "cr_esp7_c_texto_exp",
                            title = "Síntesis Técnica sin Pérdida de Datos Críticos",
                            prompt = "Al redactar el resumen pericial de una inspección técnica, ¿cuál elemento NO debe omitirse bajo ninguna circunstancia?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Las mediciones cuantitativas reales, los valores de tolerancia del fabricante y el veredicto fundado",
                                "La opinión estética del vehículo",
                                "La anécdota del cliente al llegar al taller",
                                "El clima del día de la revisión"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Un informe pericial exige trazabilidad empírica, datos cuantitativos y parámetros técnicos exactos."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp7_u02",
            track = CurriculumTrack.ESPANOL_7,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "El Cuento y la Novela Costarricense (Generación del Olimpo y del 40)",
            description = "Lectura analítica de autores nacionales: Carlos Luis Fallas, Carmen Lyra, Joaquín García Monge.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp7_c_narrativa_nacional",
                    conceptCode = "CR_ESP7_NARRATIVA_NACIONAL",
                    title = "Literatura Nacional y Denuncia Social",
                    description = "Contexto histórico, realismo social y personajes arquetípicos costarricenses.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp7_mamita_yunai",
                            conceptId = "cr_esp7_c_narrativa_nacional",
                            title = "La Novela 'Mamita Yunai' de Carlos Luis Fallas",
                            prompt = "¿Qué problemática sociohistórica denuncia principalmente la obra clásica 'Mamita Yunai' de Carlos Luis Fallas (Calufa)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Las condiciones inhumanas y explotación de los trabajadores bananeros en la zona sur y caribe por la United Fruit Company",
                                "La colonización española en Cartago",
                                "La piratería en la Isla del Coco",
                                "La construcción de la carretera interamericana"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Fallas retrata crudamente las penurias y lucha laboral de los peones bananeros frente al enclave de la transnacional."
                        ),
                        InteractiveTaskData(
                            id = "task_esp7_transfer_lenguaje_popular",
                            conceptId = "cr_esp7_c_narrativa_nacional",
                            title = "Análisis del Costumbrismo Lingüístico",
                            prompt = "En la literatura costumbrista de Aquileo J. Echeverría ('Concherías'), ¿qué función cumple la transcripción fonética del habla campesina?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Preservar con fidelidad la identidad oral, giros idiomáticos y cosmovisión del campesino tradicional costarricense",
                                "Burlarse de la falta de ortografía escolar",
                                "Inventar un idioma artificial",
                                "Copiar modelos literarios franceses"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El costumbrismo reivindica el habla popular y la riqueza léxica vernácula de las comunidades rurales de Costa Rica."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp7_u03",
            track = CurriculumTrack.ESPANOL_7,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Morfosintaxis: El Sustantivo, Adjetivo y Verbo en la Oración",
            description = "Estructura del sintagma nominal y verbal, concordancia de género y número, modos verbales.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp7_c_morfosintaxis",
                    conceptCode = "CR_ESP7_MORFOSINTAXIS",
                    title = "Concordancia Gramatical y Tiempos Verbales",
                    description = "Reglas de concordancia y uso correcto de participios y gerundios.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp7_concordancia_sujeto",
                            conceptId = "cr_esp7_c_morfosintaxis",
                            title = "Concordancia entre Sujeto y Núcleo del Predicado",
                            prompt = "¿Cuál de las siguientes oraciones mantiene concordancia gramatical perfecta en número y persona?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "El grupo de especialistas inspeccionó minuciosamente el motor.",
                                "El grupo de especialistas inspeccionaron el motor.",
                                "Los repuesto llegó a tiempo.",
                                "La cuadrilla de trabajadores terminaron temprano."
                            ),
                            correctOptionIndex = 0,
                            explanation = "El núcleo del sujeto es 'grupo' (singular), por lo que el verbo concuerda en tercera persona singular: 'inspeccionó'."
                        ),
                        InteractiveTaskData(
                            id = "task_esp7_transfer_uso_gerundio",
                            conceptId = "cr_esp7_c_morfosintaxis",
                            title = "Uso Correcto vs Incorrecto del Gerundio en Informes",
                            prompt = "En redacción técnica, ¿cuál uso del gerundio constituye un error de posterioridad inadmisible?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "'El vehículo chocó contra el muro, muriendo el conductor horas después' (gerundio de posterioridad incorrecto)",
                                "'El técnico revisó la pieza utilizando un micrómetro' (gerundio de modo correcto)",
                                "'Trabajando en equipo logramos la meta' (gerundio modal correcto)",
                                "'Estando encendido emite zumbido' (gerundio temporal correcto)"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El gerundio nunca debe usarse para expresar una acción posterior a la del verbo principal ('chocó... y murió horas después')."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp7_u04",
            track = CurriculumTrack.ESPANOL_7,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Comunicación Oral Formal: El Debate y la Exposición Técnica",
            description = "Estructuras argumentativas, refutación fundamentada, escucha activa y oratoria.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp7_c_comunicacion_oral",
                    conceptCode = "CR_ESP7_COMUNICACION_ORAL",
                    title = "Estructura del Debate y la Exposición Pericial",
                    description = "Tesis, argumentos de autoridad, datos estadísticos y conclusión persuasiva.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp7_estructura_debate",
                            conceptId = "cr_esp7_c_comunicacion_oral",
                            title = "Roles en un Debate Formal",
                            prompt = "¿Cuál es el rol primordial del moderador en un debate formal estructurado?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Garantizar la neutralidad, controlar los tiempos de intervención y otorgar el turno de la palabra con equidad",
                                "Declarar quién tiene la razón desde el inicio",
                                "Interrumpir a un solo equipo",
                                "Votar por su postura favorita"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El moderador es un árbitro imparcial que preserva el respeto a las reglas, tiempos y turnos de argumentación."
                        ),
                        InteractiveTaskData(
                            id = "task_esp7_transfer_defensa_pericial",
                            conceptId = "cr_esp7_c_comunicacion_oral",
                            title = "Defensa Oral de un Dictamen en Audiencia Arbitral",
                            prompt = "Al sustentar oralmente un diagnóstico técnico ante un tribunal o cliente escéptico, ¿cuál estrategia retórica resulta irrebatible?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Presentar la cadena de custodia de la evidencia, las mediciones con instrumentos calibrados y la norma técnica oficial infringida",
                                "Elevar la voz para intimidar a la contraparte",
                                "Afirmar que 'así se ha hecho siempre'",
                                "Apelar a la compasión del juez"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La solidez pericial descansa en evidencia científica demostrable, instrumentación certificada y normas técnicas vinculantes."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 7.º AÑO: CIENCIAS
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_cie7_mezcla_homogenea",
                            conceptId = "cr_cie7_c_materia",
                            title = "Disolución Homogénea",
                            prompt = "¿Cuál de los siguientes fluidos mecánicos constituye una mezcla homogénea (solución de una sola fase visible)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Refrigerante premezclado al 50% agua y 50% etilenglicol", "Agua con virutas de metal", "Aceite con lodo", "Arena con piedras"),
                            correctOptionIndex = 0,
                            explanation = "El agua y el etilenglicol son completamente miscibles, formando una solución homogénea transparente a nivel macroscópico."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cie7_u02",
            track = CurriculumTrack.CIENCIAS_7,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Modelos Atómicos y Estructura de la Materia",
            description = "Evolución histórica del átomo: Dalton, Thomson, Rutherford, Bohr y modelo mecánico cuántico actual.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cie7_c_atomo_modelos",
                    conceptCode = "CR_CIE7_ATOMO_MODELOS",
                    title = "Protones, Neutrones y Electrones",
                    description = "Cargas eléctricas, masa atómica y configuración electrónica básica.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cie7_particulas_subatomicas",
                            conceptId = "cr_cie7_c_atomo_modelos",
                            title = "Carga Eléctrica de los Electrones",
                            prompt = "¿Qué tipo de carga eléctrica poseen los electrones y en qué región del átomo se localizan?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Carga negativa y orbitan en la nube o corteza electrónica", "Carga positiva en el núcleo", "Carga neutra en el núcleo", "No tienen carga"),
                            correctOptionIndex = 0,
                            explanation = "Los electrones tienen carga negativa (-1) y se mueven en los orbitales de la corteza atómica alrededor del núcleo."
                        ),
                        InteractiveTaskData(
                            id = "task_cie7_transfer_conductividad_cobre",
                            conceptId = "cr_cie7_c_atomo_modelos",
                            title = "Electrones Libres y Conductividad del Cobre",
                            prompt = "El cobre (Cu) es el metal más usado en el cableado eléctrico automotriz y residencial. A nivel atómico, ¿por qué es tan excelente conductor?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Posee un electrón de valencia en su última capa que se desprende con facilidad formando una nube de electrones libres",
                                "Tiene protones que viajan por el cable",
                                "No tiene electrones en su estructura",
                                "Es un aislante perfecto"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El enlace metálico del cobre cuenta con electrones de valencia deslocalizados que fluyen instantáneamente bajo un campo eléctrico."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cie7_u03",
            track = CurriculumTrack.CIENCIAS_7,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Energía: Tipos, Transformaciones y Conservación",
            description = "Energía cinética, potencial gravitatoria, térmica, eléctrica, química y ley de conservación de la energía.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cie7_c_energia_transformaciones",
                    conceptCode = "CR_CIE7_ENERGIA_TRANSFORMACIONES",
                    title = "Principio de Conservación de la Energía",
                    description = "La energía no se crea ni se destruye, solo se transforma: ΔE = 0.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cie7_ley_conservacion",
                            conceptId = "cr_cie7_c_energia_transformaciones",
                            title = "Ley de Conservación de la Energía",
                            prompt = "¿Cuál es el postulado fundamental de la Primera Ley de la Termodinámica (Conservación de la Energía)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "La energía total de un sistema aislado permanece constante; no se crea ni se destruye, solo se transforma",
                                "La energía desaparece cuando se apaga la máquina",
                                "La energía se crea espontáneamente de la nada",
                                "Toda la energía térmica se puede convertir en trabajo sin pérdidas"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La energía universal se conserva: las formas mecánicas, químicas y eléctricas se transforman mutuamente."
                        ),
                        InteractiveTaskData(
                            id = "task_cie7_transfer_frenado_cinetica_calor",
                            conceptId = "cr_cie7_c_energia_transformaciones",
                            title = "Transformación Energética en el Frenado",
                            prompt = "Al presionar el pedal de freno de un vehículo en movimiento, ¿en qué forma de energía se convierte la energía cinética de la masa en marcha?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "En energía térmica (calor) por la fricción entre pastillas y discos",
                                "En energía nuclear",
                                "En combustible químico líquido",
                                "En gravedad pura"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Los frenos disipan la energía cinética del vehículo (1/2 mv²) convirtiéndola íntegramente en calor mediante rozamiento."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cie7_u04",
            track = CurriculumTrack.CIENCIAS_7,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Cuencas Hidrográficas, Ciclo del Agua y Suelos de Costa Rica",
            description = "Infiltración, escorrentía, erosión, horizontes del suelo y gestión del recurso hídrico.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cie7_c_recurso_hidrico",
                    conceptCode = "CR_CIE7_RECURSO_HIDRICO",
                    title = "El Ciclo Hidrológico y Conservación de Cuencas",
                    description = "Zonas de recarga acuífera y prevención de contaminación freática.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cie7_zonas_recarga",
                            conceptId = "cr_cie7_c_recurso_hidrico",
                            title = "Importancia de las Zonas de Recarga Acuífera",
                            prompt = "¿Por qué es crucial proteger las partes altas de las cordilleras volcánicas de Costa Rica como zonas de recarga?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Porque allí los suelos boscosos absorben e infiltran el agua de lluvia alimentando los mantos acuíferos subterráneos",
                                "Para que el agua corra más rápido al mar sin usarse",
                                "Para construir urbanizaciones densas",
                                "Para secar los ríos"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La infiltración en las zonas altas garantiza el caudal de agua potable para las poblaciones del Valle Central y costas."
                        ),
                        InteractiveTaskData(
                            id = "task_cie7_transfer_contaminacion_manto_freatico",
                            conceptId = "cr_cie7_c_recurso_hidrico",
                            title = "Protección Freática ante Desechos Industriales",
                            prompt = "¿Qué fenómeno ocurre si un taller lava motores sobre tierra abierta permitiendo que solventes y aceites se filtren al subsuelo?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Contaminación química irreversible del manto freático y de los pozos de agua potable cercanos",
                                "El suelo se vuelve más fértil para el pasto",
                                "El agua se vuelve más pura",
                                "No pasa nada porque el suelo destruye todos los químicos"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Los solventes clorados e hidrocarburos percolan a través del suelo y contaminan los acuíferos subterráneos durante décadas."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 7.º AÑO: ESTUDIOS SOCIALES
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_soc7_transfer_falla_sismica_cne",
                            conceptId = "cr_soc7_c_geografia_cr",
                            title = "Vulnerabilidad Sísmica y Rutas de Evacuación",
                            prompt = "Costa Rica se encuentra en la zona de subducción entre las placas Coco y Caribe. ¿Qué directriz de la Comisión Nacional de Emergencias (CNE) es obligatoria en edificios y talleres?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Planes de evacuación señalizados, anclaje estructural de maquinaria pesada y salidas de emergencia despejadas",
                                "Cerrar las puertas con llave durante temblores",
                                "Correr hacia el centro del edificio",
                                "Almacenar cilindros de gas sin amarrar"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La alta sismicidad exige anclar racks y compresores, y mantener rutas de evacuación libres de obstáculos."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc7_u02",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_7,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Climas y Regiones Socioeconómicas de Costa Rica",
            description = "Región Central, Chorotega, Pacífico Central, Brunca, Huetar Caribe y Huetar Norte (MIDEPLAN).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc7_c_regiones_mideplan",
                    conceptCode = "CR_SOC7_REGIONES_MIDEPLAN",
                    title = "Las 6 Regiones de Planificación de Costa Rica",
                    description = "Características productivas, empleo y desafíos territoriales.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc7_region_central",
                            conceptId = "cr_soc7_c_regiones_mideplan",
                            title = "La Región Central y Concentración Urbana",
                            prompt = "¿Qué característica demográfica y económica distingue a la Región Central de Costa Rica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Concentra más del 60% de la población nacional y la mayor parte del sector servicios e industrial",
                                "Es la región más despoblada del país",
                                "Se dedica exclusivamente a la pesca artesanal",
                                "No tiene carreteras pavimentadas"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La Gran Área Metropolitana (GAM) en la Región Central alberga la mayor densidad habitacional e infraestructura productiva."
                        ),
                        InteractiveTaskData(
                            id = "task_soc7_transfer_logistica_region_huetar",
                            conceptId = "cr_soc7_c_regiones_mideplan",
                            title = "Desarrollo Agroindustrial de la Región Huetar Norte",
                            prompt = "La Región Huetar Norte (San Carlos, Upala, Los Chiles) destaca como un polo agroindustrial líder. ¿Cuál es su principal producto de exportación no tradicional?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Piña, cítricos, raíces tropicales y leche",
                                "Automóviles y aviones",
                                "Cobre y carbón",
                                "Trigo y cebada"
                            ),
                            correctOptionIndex = 0,
                            explanation = "San Carlos y alrededores constituyen la mayor cuenca lechera y exportadora de piña y tubérculos de Centroamérica."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc7_u03",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_7,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Sociedades Originarias y Pueblos Indígenas de Costa Rica",
            description = "Regiones arqueológicas: Gran Nicoya, Central y Gran Diquís. Esferas de piedra precolombinas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc7_c_pueblos_originarios",
                    conceptCode = "CR_SOC7_PUEBLOS_ORIGINARIOS",
                    title = "Herencia Indígena y los 8 Pueblos Ancestrales",
                    description = "Bribri, Cabécar, Ngöbe, Maleku, Boruca, Térraba, Chorotega y Huetar.",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc7_esferas_diquis",
                            conceptId = "cr_soc7_c_pueblos_originarios",
                            title = "Las Esferas de Piedra del Diquís",
                            prompt = "Las monumentales esferas precolombinas declaradas Patrimonio Mundial de la UNESCO se originaron en:",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "El delta del Diquís en el Pacífico Sur de Costa Rica (Palmar Sur)",
                                "Las playas de Guanacaste",
                                "El volcán Irazú",
                                "El lago Arenal"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Las culturas indígenas del Diquís tallaron esferas casi perfectas de gabro y andesita con alta destreza geométrica."
                        ),
                        InteractiveTaskData(
                            id = "task_soc7_transfer_derechos_territorios",
                            conceptId = "cr_soc7_c_pueblos_originarios",
                            title = "Ley Indígena 6172 y Tenencia de Tierras",
                            prompt = "Bajo la Ley Indígena costarricense N.° 6172 de 1977, ¿cuál es el estatus jurídico de las tierras dentro de los territorios indígenas?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Son inalienables, imprescriptibles, no transferibles y exclusivas para las comunidades indígenas",
                                "Se pueden vender libremente en subasta pública a extranjeros",
                                "Son propiedad privada de los bancos",
                                "Pertenecen a las petroleras"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La ley prohíbe el traspaso o venta de tierras indígenas a personas no indígenas, garantizando su posesión ancestral."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc7_u04",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_7,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Huella Ecológica, Áreas Protegidas y Descarbonización en CR",
            description = "SINAC, Parques Nacionales, Plan Nacional de Descarbonización 2018-2050 y cambio climático.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc7_c_descarbonizacion",
                    conceptCode = "CR_SOC7_DESCARBONIZACION",
                    title = "El Plan Nacional de Descarbonización y Movilidad Eléctrica",
                    description = "Transición energética hacia una economía verde carbono neutral.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc7_sinac_cobertura",
                            conceptId = "cr_soc7_c_descarbonizacion",
                            title = "Cobertura de Áreas Silvestres Protegidas",
                            prompt = "¿Qué porcentaje aproximado del territorio continental de Costa Rica se encuentra bajo alguna categoría de protección ambiental (SINAC)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Más del 25% del territorio", "Menos del 5%", "El 50%", "El 10%"),
                            correctOptionIndex = 0,
                            explanation = "Costa Rica protege más del 25% de su superficie terrestre en parques nacionales, reservas biológicas y refugios de vida silvestre."
                        ),
                        InteractiveTaskData(
                            id = "task_soc7_transfer_flota_electrica",
                            conceptId = "cr_soc7_c_descarbonizacion",
                            title = "Transición de la Flota Vehicular a Cero Emisiones",
                            prompt = "En el marco del Plan Nacional de Descarbonización, ¿cuál es la meta clave para el sector transporte público y privado?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Electrificar la flota vehicular aprovechando que la red nacional genera más del 98% de electricidad renovable",
                                "Aumentar el uso de vehículos con motores diésel pesados",
                                "Eliminar el transporte público en autobuses",
                                "Reemplazar los autos por carretas exclusivamente"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Al tener una matriz eléctrica limpia, el reemplazo de hidrocarburos por electricidad en autos elimina casi toda la huella de CO₂."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 7.º AÑO: EDUCACIÓN CÍVICA
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_civ7_senales_transito",
                            conceptId = "cr_civ7_c_seguridad_vial",
                            title = "Clasificación de Señales de Tránsito",
                            prompt = "¿Qué significado tienen las señales de tránsito circulares de color rojo con fondo blanco según la Ley 9078?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Reglamentarias o de prohibición obligatoria", "Informativas de lugares turísticos", "Preventivas de curvas", "Obras en la vía"),
                            correctOptionIndex = 0,
                            explanation = "El círculo rojo reglamenta órdenes de acatamiento obligatorio (Alto, Ceda el paso, No virar en U)."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_civ7_u02",
            track = CurriculumTrack.CIVICA_7,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Derechos y Deberes de la Niñez y la Adolescencia (Código PANI)",
            description = "Código de la Niñez y la Adolescencia (Ley 7739), derecho a la educación, salud y libre expresión.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ7_c_codigo_ninez",
                    conceptCode = "CR_CIV7_CODIGO_NINEZ",
                    title = "Interés Superior del Menor y Derechos Fundamentales",
                    description = "Garantías procesales, protección frente al maltrato y explotación laboral.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ7_interes_superior",
                            conceptId = "cr_civ7_c_codigo_ninez",
                            title = "El Principio del Interés Superior del Niño",
                            prompt = "En la legislación costarricense y tratados internacionales, ¿qué implica el 'interés superior del menor'?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Que en toda medida o política pública, los derechos del niño o adolescente tienen prioridad absoluta sobre los intereses de los adultos o instituciones",
                                "Que los niños no tienen deberes",
                                "Que los menores no pueden asistir a la escuela",
                                "Que los tribunales no los toman en cuenta"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El principio rector obliga al Estado y familias a anteponer el bienestar integral y desarrollo de la persona menor de edad."
                        ),
                        InteractiveTaskData(
                            id = "task_civ7_transfer_trabajo_adolescente",
                            conceptId = "cr_civ7_c_codigo_ninez",
                            title = "Regulación del Régimen Especial de Trabajo Adolescente",
                            prompt = "A partir de los 15 años cumplidos, ¿qué condiciones impone el Código de Trabajo para el régimen de trabajo adolescente en un taller?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Jornada máxima de 6 horas diarias (36 semanales), no nocturna, sin labores peligrosas ni insalubres, y sin abandonar la educación formal",
                                "Jornada completa de 12 horas nocturnas",
                                "Permiso para manejar maquinaria de alto riesgo sin supervisión",
                                "No se requiere seguro social de la CCSS"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La ley protege al adolescente trabajador prohibiendo labores peligrosas y garantizando su permanencia en el sistema educativo."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_civ7_u03",
            track = CurriculumTrack.CIVICA_7,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Convivencia Democrática, Diálogo y Prevención del Bullying",
            description = "Resolución pacífica de conflictos, protocolos del MEP contra el acoso escolar y mediación.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ7_c_mediacion_conflictos",
                    conceptCode = "CR_CIV7_MEDIACION_CONFLICTOS",
                    title = "Mecanismos de Diálogo y Mediación Escolar",
                    description = "Superación de la violencia escolar mediante la comunicación asertiva y empatía.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ7_bullying_protocolo",
                            conceptId = "cr_civ7_c_mediacion_conflictos",
                            title = "Protocolo del MEP contra el Acoso y Hostigamiento Escolar",
                            prompt = "Ante una situación reiterada de agresión verbal, física o digital (ciberacoso) en un centro educativo, ¿cuál es el paso inmediato?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Activar el Protocolo de Actuación del MEP, intervenir para proteger a la víctima y notificar a la dirección y familias",
                                "Filmarlo y subirlo a redes sociales",
                                "Ignorar la agresión para no meterse en problemas",
                                "Suspender a la víctima de clases"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El protocolo oficial del MEP exige intervención inmediata, medidas de protección cautelares y apoyo psicológico."
                        ),
                        InteractiveTaskData(
                            id = "task_civ7_transfer_clima_laboral",
                            conceptId = "cr_civ7_c_mediacion_conflictos",
                            title = "Cultura de Seguridad Psicológica en el Equipo de Trabajo",
                            prompt = "En un entorno técnico o de ingeniería, ¿por qué el respeto mutuo y la ausencia de hostigamiento salvan vidas?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Porque los operarios tienen confianza para reportar fallos, dudas o condiciones inseguras de inmediato sin miedo a represalias",
                                "Para que nadie tenga que trabajar",
                                "Para ocultar los errores al cliente",
                                "No tiene ningún impacto en la seguridad"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La cultura 'no-blame' y la seguridad psicológica permiten detectar peligros antes de que deriven en catástrofes."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_civ7_u04",
            track = CurriculumTrack.CIVICA_7,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Identidad Comunal y Participación Juvenil en los Gobiernos Estudiantiles",
            description = "Elecciones estudiantiles del MEP, directivas de sección y asociaciones de desarrollo comunal (DINADECO).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_civ7_c_gobiernos_estudiantiles",
                    conceptCode = "CR_CIV7_GOBIERNOS_ESTUDIANTILES",
                    title = "El Ejercicio Democrático Estudiantil y Comunitario",
                    description = "Voto secreto, padrón electoral estudiantil y proyectos comunales de mejora.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_civ7_elecciones_mep",
                            conceptId = "cr_civ7_c_gobiernos_estudiantiles",
                            title = "Proceso Electoral Estudiantil del MEP",
                            prompt = "¿Qué valor cívico fundamental se practica en las elecciones del gobierno estudiantil de los colegios de Costa Rica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "El aprendizaje vivencial de la democracia representativa, el sufragio secreto, la rendición de cuentas y el debate de propuestas",
                                "La rivalidad personal y la división del colegio",
                                "Ganar premios en dinero",
                                "Perder lecciones escolares"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Las elecciones del MEP forman ciudadanos activos, capacitados en la deliberación y el ejercicio democrático limpio."
                        ),
                        InteractiveTaskData(
                            id = "task_civ7_transfer_dinadeco_comunidad",
                            conceptId = "cr_civ7_c_gobiernos_estudiantiles",
                            title = "Asociaciones de Desarrollo Comunal (Ley 3859)",
                            prompt = "¿Cómo puede una asociación de desarrollo comunal mejorar la seguridad vial frente a un colegio?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Gestionando ante la Municipalidad y el MOPT la demarcación de pasos peatonales, aceras accesibles y reductores de velocidad",
                                "Poniendo clavos en la carretera",
                                "Cobrando peaje a los transeúntes",
                                "Cerrando la escuela"
                            ),
                            correctOptionIndex = 0,
                            explanation = "DINADECO y las ADI articulan legalmente los proyectos de infraestructura vecinal y seguridad ciudadana con las entidades del Estado."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 7.º AÑO: INGLÉS
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_ing7_transfer_schedule_conversation",
                            conceptId = "cr_ing7_c_routines",
                            title = "Workshop Schedule Confirmation in English",
                            prompt = "A client asks: 'What time does the technical inspection start?' How do you answer correctly in English?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "'It starts at 8:00 a.m. sharp from Monday to Friday.'",
                                "'Yesterday it was raining.'",
                                "'I like coffee.'",
                                "'The engine is heavy.'"
                            ),
                            correctOptionIndex = 0,
                            explanation = "The sentence uses correct Present Simple time preposition ('at 8:00 a.m.') and business hours format."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing7_u02",
            track = CurriculumTrack.INGLES_7,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "School Environment, Places & Directions",
            description = "Prepositions of place (next to, across from, between), asking for and giving directions (A1+).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing7_c_directions",
                    conceptCode = "CR_ING7_DIRECTIONS",
                    title = "Spatial Prepositions and Navigation",
                    description = "Giving precise directions within a campus, workshop, or building.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing7_preposition_place",
                            conceptId = "cr_ing7_c_directions",
                            title = "Preposition of Place: Workshop Location",
                            prompt = "The fire extinguisher is mounted _______ the emergency exit door and the main electrical panel.",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("between", "under", "inside", "during"),
                            correctOptionIndex = 0,
                            explanation = "'Between' is used when something is situated in the middle of two distinct points or objects."
                        ),
                        InteractiveTaskData(
                            id = "task_ing7_transfer_emergency_exit",
                            conceptId = "cr_ing7_c_directions",
                            title = "Emergency Exit Guidance in English",
                            prompt = "An English-speaking auditor asks: 'Where is the assembly point?' Which instruction is accurate?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "'Exit through the green door, turn right, and gather at the soccer field.'",
                                "'I have two brothers.'",
                                "'The car is blue.'",
                                "'Tomorrow is Sunday.'"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Clear imperative directions ('Exit through...', 'turn right...', 'gather at...') ensure effective emergency communication."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing7_u03",
            track = CurriculumTrack.INGLES_7,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Healthy Habits, Nutrition & Workplace Wellness",
            description = "Countable vs uncountable nouns, food groups, hydration, and avoiding fatigue (A2).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing7_c_wellness",
                    conceptCode = "CR_ING7_WELLNESS",
                    title = "Occupational Health and Nutrition",
                    description = "Describing daily meals, hydration, and physical well-being at school and work.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing7_countable_water",
                            conceptId = "cr_ing7_c_wellness",
                            title = "Countable vs Uncountable Nouns",
                            prompt = "Choose the correct quantifier: 'Heavy manual labor requires drinking _______ water every day.'",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("a lot of / plenty of", "many", "several", "a few"),
                            correctOptionIndex = 0,
                            explanation = "'Water' is an uncountable substance noun; 'a lot of' or 'plenty of' is the grammatically correct quantifier."
                        ),
                        InteractiveTaskData(
                            id = "task_ing7_transfer_fatigue_alert",
                            conceptId = "cr_ing7_c_wellness",
                            title = "Communicating Physical Distress in English",
                            prompt = "How should a technician clearly tell an international supervisor that they feel dizzy and need first aid?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "'Excuse me, I feel dizzy and dehydrated; I need to step aside and drink water.'",
                                "'I am very happy today.'",
                                "'Look at that plane.'",
                                "'The engine is working well.'"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Communicating health status directly and politely ('I feel dizzy... need to step aside') prevents workplace accidents."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing7_u04",
            track = CurriculumTrack.INGLES_7,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Environmental Protection, Recycling & National Parks",
            description = "Can/Can't for abilities and permissions, zero waste, conservation in Costa Rica (A2).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing7_c_conservation",
                    conceptCode = "CR_ING7_CONSERVATION",
                    title = "Eco-Tourism and Environmental Rules",
                    description = "Modal verbs for rules and wildlife protection.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing7_modal_park_rule",
                            conceptId = "cr_ing7_c_conservation",
                            title = "Modal Verbs in National Park Signs",
                            prompt = "Which modal sentence represents a strict prohibition in Corcovado National Park?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Visitors cannot feed wild animals under any circumstance.",
                                "Visitors can feed monkeys if they are hungry.",
                                "Visitors must throw trash in the river.",
                                "Visitors might hunt toucans."
                            ),
                            correctOptionIndex = 0,
                            explanation = "'Cannot' indicates strict absence of permission or absolute prohibition."
                        ),
                        InteractiveTaskData(
                            id = "task_ing7_transfer_waste_sorting",
                            conceptId = "cr_ing7_c_conservation",
                            title = "Sorting Recyclable Materials in English",
                            prompt = "Where should scrap aluminum and copper wire cuttings be placed according to international signage?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "In the 'Metals & Recyclables' container",
                                "In the 'Organic Food Waste' bin",
                                "In the 'Hazardous Bio-medical Waste' bag",
                                "Into the regular paper basket"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Scrap metals belong in the designated 'Metals & Recyclables' bin for industrial recycling."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // CIENCIAS III CICLO
    // ══════════════════════════════════════════════════════════════════════════
    val CIENCIAS_III_CICLO_UNITS = listOf(
        CourseUnitData(
            id = "cr_cien_iii_u01",
            track = CurriculumTrack.CIENCIAS_III_CICLO,
            unitNumber = 1,
            targetMonth = 4,
            monthName = "Abril",
            title = "Estructura Celular y Sistemas del Cuerpo Humano",
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
                        ),
                        InteractiveTaskData(
                            id = "task_cien_iii_transfer_membrana",
                            conceptId = "cr_cien_c_celula",
                            title = "Permeabilidad Selectiva de la Membrana Plasmática",
                            prompt = "La membrana celular regula qué sustancias entran y salen de la célula. ¿A qué componente mecánico o filtro equivale esta función en un motor?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Al filtro de combustible y aire que retiene impurezas permitiendo solo el paso de fluido limpio",
                                "Al caño de escape",
                                "Al pedal del acelerador",
                                "A la pintura de la carrocería"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La membrana semipermeable actúa análogamente a un filtro microscópico de alta precisión con compuertas reguladas."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cien_iii_u02",
            track = CurriculumTrack.CIENCIAS_III_CICLO,
            unitNumber = 2,
            targetMonth = 6,
            monthName = "Junio",
            title = "Leyes de la Herencia Biológica y Genética Elemental",
            description = "ADN, genes, cromosomas, alelos dominantes y recesivos, y mutaciones genéticas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cien_iii_c_genetica",
                    conceptCode = "CR_CIEN_III_GENETICA",
                    title = "El ADN como Portador de la Información Biológica",
                    description = "Estructura de doble hélice, bases nitrogenadas y transmisión de caracteres.",
                    targetMonth = 6,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cien_iii_adn_bases",
                            conceptId = "cr_cien_iii_c_genetica",
                            title = "Bases Nitrogenadas del ADN",
                            prompt = "¿Cuáles son las cuatro bases nitrogenadas que codifican el mensaje genético en la molécula de ADN?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Adenina (A), Timina (T), Citosina (C) y Guanina (G)",
                                "Carbono, Hidrógeno, Oxígeno y Nitrógeno",
                                "Hierro, Cobre, Aluminio y Plomo",
                                "Glucosa, Sacarosa, Fructosa y Lactosa"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El código genético universal del ADN se compone de los pares de bases A-T y C-G."
                        ),
                        InteractiveTaskData(
                            id = "task_cien_iii_transfer_mutaciones_radiacion",
                            conceptId = "cr_cien_iii_c_genetica",
                            title = "Agentes Mutagénicos en Ensayos No Destructivos",
                            prompt = "En la inspección de soldaduras mediante radiografía industrial (Rayos X o Gamma), ¿por qué los técnicos usan dosímetros y delantales plomados?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Para evitar que la radiación ionizante rompa las cadenas de ADN celular provocando mutaciones o cáncer",
                                "Para evitar ensuciarse de grasa",
                                "Para mantener caliente el cuerpo",
                                "Para que las fotos salgan más nítidas"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La radiación ionizante altera los enlaces químicos del ADN celular; el plomo bloquea estos fotones de alta energía."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cien_iii_u03",
            track = CurriculumTrack.CIENCIAS_III_CICLO,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Cinemática y Dinámica: Rapidez, Velocidad y Fuerzas",
            description = "Movimiento rectilíneo uniforme (MRU), aceleración, fuerza neta y masa inercial.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cien_iii_c_fuerzas_mru",
                    conceptCode = "CR_CIEN_III_FUERZAS_MRU",
                    title = "Leyes del Movimiento y Vectores de Fuerza",
                    description = "Relación entre fuerza, masa y aceleración (F = m × a).",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cien_iii_segunda_ley",
                            conceptId = "cr_cien_iii_c_fuerzas_mru",
                            title = "Segunda Ley de Newton",
                            prompt = "¿Qué ocurre con la aceleración de un vehículo si se duplica la fuerza del motor manteniendo constante la masa?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Se duplica exactamente (a es directamente proporcional a F)", "Se reduce a la mitad", "Permanece igual", "Se hace cero"),
                            correctOptionIndex = 0,
                            explanation = "Por la 2da ley (a = F/m), la aceleración es directamente proporcional a la fuerza neta aplicada."
                        ),
                        InteractiveTaskData(
                            id = "task_cien_iii_transfer_inercia_carga",
                            conceptId = "cr_cien_iii_c_fuerzas_mru",
                            title = "Inercia y Carga en Transporte Pesado",
                            prompt = "¿Por qué un camión cargado con 20 toneladas requiere mucho más tiempo y distancia de frenado que el mismo camión vacío a igual velocidad?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Por su mayor masa inercial: a mayor masa, mayor es la resistencia a cambiar su estado de movimiento",
                                "Porque las llantas se vuelven más resbaladizas con el peso",
                                "Porque el motor empuja hacia adelante al frenar",
                                "Porque el viento lo empuja más fuerte"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La masa es la medida cuantitativa de la inercia; mayor masa exige disipar mucha mayor cantidad de movimiento lineal (p = mv)."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cien_iii_u04",
            track = CurriculumTrack.CIENCIAS_III_CICLO,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Química: Enlaces Químicos, Estructura Molecular y Reacciones",
            description = "Enlace iónico, covalente y metálico. Ley de conservación de la masa de Lavoisier.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cien_iii_c_enlaces_quimicos",
                    conceptCode = "CR_CIEN_III_ENLACES_QUIMICOS",
                    title = "Enlaces Iónicos, Covalentes y Metálicos",
                    description = "Electronegatividad, transferencia y compartición de pares de electrones.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cien_iii_enlace_covalente",
                            conceptId = "cr_cien_iii_c_enlaces_quimicos",
                            title = "Definición de Enlace Covalente",
                            prompt = "¿Cómo se forma un enlace químico covalente entre dos átomos no metálicos?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Mediante la compartición de uno o más pares de electrones de valencia",
                                "Mediante la transferencia total de electrones formando iones opuestos",
                                "Por atracción gravitatoria",
                                "Por fricción física entre los núcleos"
                            ),
                            correctOptionIndex = 0,
                            explanation = "En los enlaces covalentes (ej. agua, hidrocarburos), los átomos comparten pares de electrones para alcanzar estabilidad octeto."
                        ),
                        InteractiveTaskData(
                            id = "task_cien_iii_transfer_corrosion_galvanica",
                            conceptId = "cr_cien_iii_c_enlaces_quimicos",
                            title = "Corrosión Galvánica en Aleaciones de Carrocería",
                            prompt = "Al unir una pieza de aluminio con un perno de acero en presencia de humedad salina, ¿qué fenómeno electroquímico ocurre y por qué?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Corrosión galvánica: el aluminio (más anódico/reactivo) cede electrones rápidamente y se corroe para proteger al acero",
                                "El perno se suelda solo al aluminio",
                                "Los metales se vuelven oro puro",
                                "No pasa nada porque ambos son metales"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La diferencia de potencial de reducción entre dos metales distintos en presencia de un electrolito genera una pila galvánica destructiva."
                        )
                    )
                )
            )
        )
    )
}
