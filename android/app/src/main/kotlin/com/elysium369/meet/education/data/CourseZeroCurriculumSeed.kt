package com.elysium369.meet.education.data

import com.elysium369.meet.education.domain.CognitiveLevel
import com.elysium369.meet.education.domain.CurriculumGranularity
import com.elysium369.meet.education.domain.CurriculumSourceAnchor
import com.elysium369.meet.education.domain.CurriculumSourceKind
import com.elysium369.meet.education.domain.EpistemicTruthState
import com.elysium369.meet.education.domain.StageType


enum class TaskType {
    SPATIAL_PLACEMENT,
    CURRENCY_CALCULATOR,
    MULTIPLE_CHOICE,
    TECHNICAL_SEQUENCE,
}

data class InteractiveTaskData(
    val id: String,
    val conceptId: String,
    val title: String,
    val prompt: String,
    val type: TaskType,
    val isTransferTask: Boolean,
    val options: List<String> = emptyList(),
    val correctOptionIndex: Int = 0,
    val targetAmountCrc: Int = 0,
    val misconceptionCodes: Map<Int, String> = emptyMap(),
    val explanation: String,
    val environmentContext: String = "FORGE_3D_ROOM",
)

data class CurriculumConceptData(
    val id: String,
    val conceptCode: String,
    val title: String,
    val description: String,
    val targetMonth: Int,
    val truthState: EpistemicTruthState = EpistemicTruthState.AUTHORITATIVE,
    val tasks: List<InteractiveTaskData> = emptyList(),
)

data class CourseUnitData(
    val id: String,
    val track: CurriculumTrack,
    val unitNumber: Int,
    val targetMonth: Int,
    val monthName: String,
    val title: String,
    val description: String,
    val estimatedLessons: Int,
    val concepts: List<CurriculumConceptData>,
)

object CourseZeroCurriculumSeed {

    val MATEMATICA_SOURCE_ANCHOR = CurriculumSourceAnchor(
        sourceId = "cr_mep_matematicas_1_2026",
        authorityId = "cr_mep_dcur",
        canonicalTitle = "Programa de Estudio Matemática I y II Ciclos — Distribución Mensual Oficial 2026",
        sourceLocator = "https://www.mep.go.cr/curriculo/matematica/2026/1-ano",
        documentHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        effectiveYear = 2026,
        sourceKind = CurriculumSourceKind.OFFICIAL_MONTHLY_DISTRIBUTION,
        sourceGranularity = CurriculumGranularity.OFFICIAL_MONTHLY,
        isOfficial = true,
    )

    val FONTANERIA_SOURCE_ANCHOR = CurriculumSourceAnchor(
        sourceId = "cr_mep_artes_industriales_7_2026",
        authorityId = "cr_mep_detce",
        canonicalTitle = "Programa de Estudio Artes Industriales III Ciclo — Sub-área Fontanería y Redes 2026",
        sourceLocator = "https://www.mep.go.cr/tecnica/artes-industriales/2026/7-ano-fontaneria",
        documentHash = "a3f5c9e2b810d4739182abcf4e7293b610283c9d748291aefc3928174628192a",
        effectiveYear = 2026,
        sourceKind = CurriculumSourceKind.CANONICAL_CURRICULUM,
        sourceGranularity = CurriculumGranularity.OFFICIAL_UNIT_SEQUENCE,
        isOfficial = true,
    )

    val MATEMATICA_1_UNITS: List<CourseUnitData> = listOf(
        CourseUnitData(
            id = "cr_mat1_u02",
            track = CurriculumTrack.MATEMATICA_1,
            unitNumber = 1,
            targetMonth = 2,
            monthName = "Febrero",
            title = "Geometría y Espacio: El Mundo de las Posiciones",
            description = "Ubicación espacial, tamaño, longitud, anchura/espesor y distancia.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat1_c_spatial_pos",
                    conceptCode = "CR_MAT1_SPATIAL_POS",
                    title = "Posiciones relativas en el espacio",
                    description = "Conceptos espaciales: detrás, delante, al lado, entre, cerca, lejos",
                    targetMonth = 2,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat1_spatial_cat_house",
                            conceptId = "cr_mat1_c_spatial_pos",
                            title = "Don Bigotes en la Casita",
                            prompt = "Don Bigotes (el gato del taller) se escondió para tomar una siesta. Mira el patio: ¿En qué posición está el gato respecto a la casita?",
                            type = TaskType.SPATIAL_PLACEMENT,
                            isTransferTask = false,
                            options = listOf(
                                "Detrás de la casita",
                                "Delante de la casita",
                                "Al lado de la casita",
                                "Encima del techo",
                            ),
                            correctOptionIndex = 0,
                            misconceptionCodes = mapOf(
                                1 to "MISCONCEPTION_REVERSED_DELANTE_DETRAS",
                                2 to "MISCONCEPTION_LATERAL_CONFUSION",
                                3 to "MISCONCEPTION_VERTICAL_OVERLAY",
                            ),
                            explanation = "El gato está cubierto por la pared posterior, es decir, DETRÁS de la casita.",
                        ),
                        InteractiveTaskData(
                            id = "task_mat1_spatial_transfer_exhaust",
                            conceptId = "cr_mat1_c_spatial_pos",
                            title = "Desafío de Transferencia: Escape del Auto",
                            prompt = "En un vehículo real, ¿dónde se ubica el silenciador final del escape respecto al motor delantero?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true, // Enables mastery past 0.750
                            options = listOf(
                                "Detrás del motor, hacia la parte trasera",
                                "Delante del parachoques frontal",
                                "Encima del capó del motor",
                                "Dentro del volante de dirección",
                            ),
                            correctOptionIndex = 0,
                            misconceptionCodes = mapOf(
                                1 to "MISCONCEPTION_AUTOMOTIVE_LAYOUT_INVERTED",
                            ),
                            explanation = "¡Excelente transferencia! El silenciador viaja detrás del motor y del catalizador hacia la cola del vehículo.",
                        ),
                    ),
                ),
                CurriculumConceptData(
                    id = "cr_mat1_c_dimensions",
                    conceptCode = "CR_MAT1_DIMENSIONS",
                    title = "Dimensiones comparativas de objetos",
                    description = "Comparación de tamaño, longitud, anchura y espesor.",
                    targetMonth = 2,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat1_dim_wrench",
                            conceptId = "cr_mat1_c_dimensions",
                            title = "La Llave Más Larga",
                            prompt = "Observa las dos llaves fijas sobre el banco de trabajo: la Llave A mide 10 cm y la Llave B mide 18 cm. ¿Cuál es más larga?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Llave B (18 cm)", "Llave A (10 cm)", "Son de igual longitud"),
                            correctOptionIndex = 0,
                            explanation = "18 cm es mayor que 10 cm, por lo tanto la Llave B es más larga.",
                        )
                    )
                ),
            ),
        ),
        CourseUnitData(
            id = "cr_mat1_u03",
            track = CurriculumTrack.MATEMATICA_1,
            unitNumber = 2,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Números: Cantidad y Conteo Inicial",
            description = "Cantidad, conteo, representaciones numéricas, unidades y decenas < 100.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat1_c_counting_100",
                    conceptCode = "CR_MAT1_COUNTING_100",
                    title = "Conteo y valor posicional hasta 100",
                    description = "Cantidad, orden y valor posicional de unidades y decenas",
                    targetMonth = 3,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat1_counting_bolts",
                            conceptId = "cr_mat1_c_counting_100",
                            title = "Conteo de Tornillos",
                            prompt = "Tenemos 2 cajas de 10 tornillos cada una y 5 tornillos sueltos. ¿Cuántos tornillos hay en total?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("25 tornillos", "15 tornillos", "7 tornillos", "20 tornillos"),
                            correctOptionIndex = 0,
                            misconceptionCodes = mapOf(1 to "MISCONCEPTION_COUNTING_ADDITIVE_FLAT"),
                            explanation = "2 decenas son 20, más 5 unidades sueltas = 25 tornillos.",
                        ),
                        InteractiveTaskData(
                            id = "task_mat1_counting_transfer_tires",
                            conceptId = "cr_mat1_c_counting_100",
                            title = "Desafío de Transferencia: Llantas de la Flota",
                            prompt = "Un taller inspecciona 6 automóviles de 4 ruedas cada uno. ¿Cuántas ruedas inspecciona en total (contando de 4 en 4 o agrupando)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("24 ruedas", "10 ruedas", "20 ruedas", "30 ruedas"),
                            correctOptionIndex = 0,
                            explanation = "6 autos x 4 ruedas = 24 ruedas. ¡Gran aplicación de valor y conteo agrupado!",
                        ),
                    ),
                ),
            ),
        ),
        CourseUnitData(
            id = "cr_mat1_u04",
            track = CurriculumTrack.MATEMATICA_1,
            unitNumber = 3,
            targetMonth = 4,
            monthName = "Abril",
            title = "Simbología y Medidas: Trazado y Longitud",
            description = "Trazado 0-9, ordinales hasta décimo, líneas de posición, metro y centímetro.",
            estimatedLessons = 8,
            concepts = emptyList(),
        ),
        CourseUnitData(
            id = "cr_mat1_u05",
            track = CurriculumTrack.MATEMATICA_1,
            unitNumber = 4,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Economía y Datos: Mi Primera Tienda Costarricense",
            description = "Unidad monetaria colón, monedas oficiales, datos cualitativos y variabilidad.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat1_c_currency_crc",
                    conceptCode = "CR_MAT1_CURRENCY_CRC",
                    title = "Moneda de Costa Rica (Colón)",
                    description = "Reconocimiento y denominaciones de monedas: 5, 10, 25, 50, 100 y 500 colones",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat1_currency_juice",
                            conceptId = "cr_mat1_c_currency_crc",
                            title = "Comprar Jugo en la Pulpería",
                            prompt = "En la pulpería de Doña Rosa, un jugo de naranja natural cuesta exactamente ₡375. Selecciona las monedas de colones para pagar el monto exacto:",
                            type = TaskType.CURRENCY_CALCULATOR,
                            isTransferTask = false,
                            targetAmountCrc = 375,
                            explanation = "₡375 se compone por ejemplo de: 3 monedas de ₡100 + 1 de ₡50 + 1 de ₡25 = ₡375.",
                        ),
                        InteractiveTaskData(
                            id = "task_mat1_currency_transfer_change",
                            conceptId = "cr_mat1_c_currency_crc",
                            title = "Desafío de Transferencia: Vuelto de ₡500",
                            prompt = "Pagas una galleta que cuesta ₡350 con una moneda de ₡500. ¿Cuánto vuelto exacto te debe entregar el pulpero?",
                            type = TaskType.CURRENCY_CALCULATOR,
                            isTransferTask = true,
                            targetAmountCrc = 150,
                            explanation = "₡500 - ₡350 = ₡150 de vuelto exacto (1 moneda de ₡100 + 1 de ₡50).",
                        ),
                    ),
                ),
            ),
        ),
        CourseUnitData(
            id = "cr_mat1_u06",
            track = CurriculumTrack.MATEMATICA_1,
            unitNumber = 5,
            targetMonth = 6,
            monthName = "Junio",
            title = "Operaciones y Figuras: Suma, Resta y Polígonos",
            description = "Suma/resta inicial, identificación y trazo de triángulos, cuadriláteros y polígonos.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat1_c_addition_sub",
                    conceptCode = "CR_MAT1_ADDITION_SUB",
                    title = "Adición y Sustracción básica",
                    description = "Comprensión y resolución de operaciones elementales de adición y resta.",
                    targetMonth = 6,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat1_add_fuse",
                            conceptId = "cr_mat1_c_addition_sub",
                            title = "Caja de Fusibles",
                            prompt = "Tenías 8 fusibles nuevos y usaste 3 para reparar las luces del auto. ¿Cuántos fusibles te quedan?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("5 fusibles", "11 fusibles", "4 fusibles", "6 fusibles"),
                            correctOptionIndex = 0,
                            explanation = "8 - 3 = 5 fusibles restantes.",
                        ),
                    ),
                ),
            ),
        ),
        CourseUnitData(
            id = "cr_mat1_u07",
            track = CurriculumTrack.MATEMATICA_1,
            unitNumber = 6,
            targetMonth = 7,
            monthName = "Julio",
            title = "Masa y Cronometría: Peso y Nociones de Tiempo",
            description = "Comparación de peso e intervalos temporales (amanecer, día, tarde, noche).",
            estimatedLessons = 8,
            concepts = emptyList(),
        ),
        CourseUnitData(
            id = "cr_mat1_u08",
            track = CurriculumTrack.MATEMATICA_1,
            unitNumber = 7,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Regularidades y Estadística: Patrones y Frecuencias",
            description = "Patrones ABAB, sucesiones numéricas, recolección de frecuencias y operaciones.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat1_c_patterns",
                    conceptCode = "CR_MAT1_PATTERNS",
                    title = "Patrones y regularidades",
                    description = "Identificación y prolongación de patrones geométricos y numéricos.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat1_pattern_lights",
                            conceptId = "cr_mat1_c_patterns",
                            title = "Patrón de Semáforo Taller",
                            prompt = "¿Qué color sigue en la secuencia: Rojo, Verde, Rojo, Verde, Rojo, ...?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Verde", "Rojo", "Azul", "Amarillo"),
                            correctOptionIndex = 0,
                            explanation = "Es un patrón alternado A-B-A-B: después de Rojo sigue Verde.",
                        )
                    )
                ),
            ),
        ),
        CourseUnitData(
            id = "cr_mat1_u09",
            track = CurriculumTrack.MATEMATICA_1,
            unitNumber = 8,
            targetMonth = 9,
            monthName = "Setiembre",
            title = "Aritmética Profunda: Doble, Mitad y Problemas",
            description = "Doble y mitad, problemas aditivos < 100, símbolos +, -, = y cálculo mental.",
            estimatedLessons = 8,
            concepts = emptyList(),
        ),
        CourseUnitData(
            id = "cr_mat1_u10",
            track = CurriculumTrack.MATEMATICA_1,
            unitNumber = 9,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Cuerpos Geométricos y Capacidad: Cajas e Igualdades",
            description = "Cuerpos con forma de caja, capacidad, equivalencias y expresiones matemáticas.",
            estimatedLessons = 8,
            concepts = emptyList(),
        ),
        CourseUnitData(
            id = "cr_mat1_u11",
            track = CurriculumTrack.MATEMATICA_1,
            unitNumber = 10,
            targetMonth = 11,
            monthName = "Noviembre",
            title = "Probabilidad y Cierre: Situaciones Aleatorias y Seguras",
            description = "Diferenciación entre situaciones seguras y aleatorias, consolidación y transferencia.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat1_c_probability",
                    conceptCode = "CR_MAT1_PROBABILITY",
                    title = "Eventos aleatorios vs eventos seguros",
                    description = "Identificación de sucesos seguros, probables e imposibles.",
                    targetMonth = 11,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat1_prob_sun",
                            conceptId = "cr_mat1_c_probability",
                            title = "Amanecer en Costa Rica",
                            prompt = "En San José o Guanacaste, que el sol salga mañana por la mañana es un evento:",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Seguro", "Imposible", "Poco probable"),
                            correctOptionIndex = 0,
                            explanation = "El amanecer diario es un fenómeno astronómico predecible y seguro.",
                        )
                    )
                ),
            ),
        ),
    )

    val FONTANERIA_7_UNITS: List<CourseUnitData> = listOf(
        CourseUnitData(
            id = "cr_font7_u01",
            track = CurriculumTrack.FONTANERIA_7,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Fundamentos y Seguridad Ocupacional en Fontanería",
            description = "Conservación del agua, normas de seguridad y equipo de protección personal.",
            estimatedLessons = 10,
            concepts = emptyList(),
        ),
        CourseUnitData(
            id = "cr_font7_u02",
            track = CurriculumTrack.FONTANERIA_7,
            unitNumber = 2,
            targetMonth = 4,
            monthName = "Abril",
            title = "Herramientas y Equipos de Fontanería",
            description = "Uso y calibración de llaves ajustables, terrajas, cortatubos y manómetros.",
            estimatedLessons = 12,
            concepts = emptyList(),
        ),
        CourseUnitData(
            id = "cr_font7_u03",
            track = CurriculumTrack.FONTANERIA_7,
            unitNumber = 3,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Materiales y Técnicas con PVC / CPVC",
            description = "Corte, biselado, limpieza y soldadura química de tuberías PVC potable y sanitario.",
            estimatedLessons = 14,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_font7_c_pvc_joinery",
                    conceptCode = "CR_FONT7_PVC_JOINERY",
                    title = "Técnicas de unión y soldadura en tubería PVC",
                    description = "Proceso técnico de unión química de PVC potable y sanitario cumpliendo norma técnica.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_font7_pvc_holding_time",
                            conceptId = "cr_font7_c_pvc_joinery",
                            title = "Unión Química de PVC: Retención Inicial",
                            prompt = "¿Por qué la norma técnica exige mantener presionada la unión de PVC durante 30 segundos inmediatamente tras insertar el tubo con un cuarto de giro?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Para evitar que la conicidad del accesorio expulse el tubo antes del agarre inicial del solvente.",
                                "Para que el aire caliente enfríe la tubería rápidamente.",
                                "Para eliminar el exceso de cemento por capilaridad.",
                                "Para que el tubo cambie de color indicando que ya soldó.",
                            ),
                            correctOptionIndex = 0,
                            misconceptionCodes = mapOf(
                                1 to "MISCONCEPTION_SOLVENT_WELD_THERMAL",
                                2 to "MISCONCEPTION_CAPILLARY_DRAIN",
                            ),
                            explanation = "El cemento solvente disuelve temporalmente las paredes plásticas. La ligera conicidad interior expulsaría el tubo si no se sostiene durante 30 s.",
                        ),
                        InteractiveTaskData(
                            id = "task_font7_pvc_transfer_cure",
                            conceptId = "cr_font7_c_pvc_joinery",
                            title = "Desafío de Transferencia: Prueba de Presión a 60 PSI",
                            prompt = "En una instalación de agua potable residencial en Alajuela a 24°C, ¿cuál es el tiempo mínimo de curado antes de someter la red a prueba de presión hidrostática?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Al menos 2 horas para tubería de hasta 1 1/4\" antes de presurizar",
                                "Inmediatamente después de soltar los 30 segundos",
                                "5 minutos si hace sol",
                                "30 días obligatorios",
                            ),
                            correctOptionIndex = 0,
                            explanation = "¡Excelente transferencia! El cemento solvente requiere evaporar los compuestos volátiles y recuperar la rigidez estructural completa antes de someterse a presión.",
                        ),
                    ),
                ),
            ),
        ),
        CourseUnitData(
            id = "cr_font7_u04",
            track = CurriculumTrack.FONTANERIA_7,
            unitNumber = 4,
            targetMonth = 6,
            monthName = "Junio",
            title = "Diagnóstico de Presión y Fugas en Redes Domiciliares",
            description = "Detección acústica, prueba hidrostática y localización de fugas menores.",
            estimatedLessons = 14,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_font7_c_leak_diag",
                    conceptCode = "CR_FONT7_LEAK_DIAG",
                    title = "Diagnóstico y reparación de fugas menores",
                    description = "Detección y resolución técnica de averías en grifería y llaves de paso.",
                    targetMonth = 6,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_font7_valve_gasket",
                            conceptId = "cr_font7_c_leak_diag",
                            title = "Goteo Continuo en Grifería",
                            prompt = "Una llave de chorro presenta goteo constante a pesar de estar apretada firmemente. ¿Cuál es el componente de desgaste más probable a reemplazar?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "El empaque de hule (cuerito/o-ring) del vástago",
                                "El medidor de agua de la acera",
                                "Toda la tubería de la pared",
                                "La cinta de teflón de la rosca exterior",
                            ),
                            correctOptionIndex = 0,
                            explanation = "El empaque o arandela de elastómero sella el asiento de la válvula; al desgastarse o deformarse, permite el paso continuo de agua.",
                        ),
                    ),
                ),
            ),
        ),
    )
}
