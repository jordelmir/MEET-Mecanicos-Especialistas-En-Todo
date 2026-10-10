package com.elysium369.meet.education.data

import com.elysium369.meet.education.domain.EpistemicTruthState

/**
 * Catálogo Curricular Completo para I y II Ciclos (Primaria) — MEP Costa Rica 2026.
 * Incluye Matemática (2.º a 6.º), Ciencias, Español, Estudios Sociales e Inglés Primaria.
 * Cada materia contiene múltiples unidades con conceptos fundamentales y tareas de transferencia (PISA).
 */
object PrimaryCurriculumSeed {

    // ══════════════════════════════════════════════════════════════════════════
    // 2.º AÑO: MATEMÁTICA
    // ══════════════════════════════════════════════════════════════════════════
    val MATEMATICA_2_UNITS = listOf(
        CourseUnitData(
            id = "cr_mat2_u01",
            track = CurriculumTrack.MATEMATICA_2,
            unitNumber = 1,
            targetMonth = 2,
            monthName = "Febrero",
            title = "Números Naturales hasta 1000 y Valor Posicional",
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
        ),
        CourseUnitData(
            id = "cr_mat2_u02",
            track = CurriculumTrack.MATEMATICA_2,
            unitNumber = 2,
            targetMonth = 4,
            monthName = "Abril",
            title = "Suma y Resta con Llevada hasta 1000",
            description = "Algoritmos de adición y sustracción reagrupando unidades y decenas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat2_c_adicion_llevada",
                    conceptCode = "CR_MAT2_ADICION_LLEVADA",
                    title = "Algoritmo de la Adición con Reagrupación",
                    description = "Cálculo vertical de sumas con transformación de unidades a decenas.",
                    targetMonth = 4,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat2_suma_vertical",
                            conceptId = "cr_mat2_c_adicion_llevada",
                            title = "Cálculo de Inventario de Tuercas",
                            prompt = "Un mecánico tiene 158 tuercas métricas en una caja y compra 275 más. ¿Cuántas tuercas tiene en total?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("433 tuercas", "423 tuercas", "323 tuercas", "443 tuercas"),
                            correctOptionIndex = 0,
                            explanation = "158 + 275: 8 + 5 = 13 (llevamos 1); 1 + 5 + 7 = 13 (llevamos 1); 1 + 1 + 2 = 4. Total = 433."
                        ),
                        InteractiveTaskData(
                            id = "task_mat2_transfer_cambio",
                            conceptId = "cr_mat2_c_adicion_llevada",
                            title = "Vuelto en Colones en Venta de Repuestos",
                            prompt = "Un fusible cuesta ₡350. Un cliente paga con una moneda de ₡500. ¿Cuál es el vuelto exacto que debe entregar?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("₡150 (500 - 350 = 150)", "₡250", "₡100", "₡50"),
                            correctOptionIndex = 0,
                            explanation = "Restando ₡350 de ₡500: 500 - 350 = ₡150 de cambio."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat2_u03",
            track = CurriculumTrack.MATEMATICA_2,
            unitNumber = 3,
            targetMonth = 7,
            monthName = "Julio",
            title = "Cuerpos Geométricos y Figuras Planas",
            description = "Identificación de cilindros, prismas, conos, esferas, cuadrados y triángulos en el entorno.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat2_c_geometria",
                    conceptCode = "CR_MAT2_GEOMETRIA",
                    title = "Cuerpos Redondos y Poliedros en Herramientas",
                    description = "Distinción entre caras planas y superficies curvas en objetos cotidianos.",
                    targetMonth = 7,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat2_cilindro_piston",
                            conceptId = "cr_mat2_c_geometria",
                            title = "Forma Geométrica del Pistón",
                            prompt = "¿Qué cuerpo geométrico tridimensional describe con mayor precisión la forma de un pistón o una lata de aceite?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Cilindro (dos bases circulares planas y una cara curva)", "Cubo", "Pirámide triangular", "Esfera perfecta"),
                            correctOptionIndex = 0,
                            explanation = "El cilindro posee bases circulares planas paralelas y una superficie lateral curva, idéntica a un pistón."
                        ),
                        InteractiveTaskData(
                            id = "task_mat2_transfer_esfera_rodamiento",
                            conceptId = "cr_mat2_c_geometria",
                            title = "Elementos Rodantes de un Cojinete",
                            prompt = "Los balines internos de un rodamiento mecánico giran suavemente en cualquier dirección. ¿Qué forma geométrica poseen?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("Esferas (cuerpos redondos sin vértices ni aristas)", "Prismas rectangulares", "Conos truncados", "Hexágonos planos"),
                            correctOptionIndex = 0,
                            explanation = "Los balines de rodamientos son esferas perfectas, lo que minimiza la fricción al rodar en cualquier eje."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat2_u04",
            track = CurriculumTrack.MATEMATICA_2,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Medidas de Longitud y Capacidad (Metro y Litro)",
            description = "Estimación y medición con el metro, centímetro y litro en recipientes y piezas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat2_c_medidas",
                    conceptCode = "CR_MAT2_MEDIDAS",
                    title = "Uso del Metro y el Litro en Mediciones Prácticas",
                    description = "Comparación de dimensiones y capacidades en recipientes estándar.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat2_metro_cm",
                            conceptId = "cr_mat2_c_medidas",
                            title = "Equivalencia de 1 Metro en Centímetros",
                            prompt = "Una manguera de aire comprimido mide 1 metro de largo. ¿A cuántos centímetros equivale?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("100 centímetros", "10 centímetros", "1000 centímetros", "50 centímetros"),
                            correctOptionIndex = 0,
                            explanation = "1 metro (m) equivale exactamente a 100 centímetros (cm)."
                        ),
                        InteractiveTaskData(
                            id = "task_mat2_transfer_litro_aceite",
                            conceptId = "cr_mat2_c_medidas",
                            title = "Capacidad del Cárter del Motor",
                            prompt = "El cárter de una motocicleta requiere 2 litros de aceite. Si compras botellas de medio litro (1/2 L), ¿cuántas botellas necesitas?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("4 botellas de medio litro (1/2 + 1/2 + 1/2 + 1/2 = 2 L)", "2 botellas", "1 botella", "6 botellas"),
                            correctOptionIndex = 0,
                            explanation = "Cada litro contiene dos mitades de litro (2 × 1/2 = 1 L). Para 2 litros se requieren 4 botellas de 1/2 L."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 3.º AÑO: MATEMÁTICA
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_mat3_transfer_parrilla",
                            conceptId = "cr_mat3_c_multiplicacion",
                            title = "Arreglo Rectangular de Tornillos",
                            prompt = "En un panel organizador hay 7 filas de tornillos con 9 tornillos en cada fila. ¿Cuántos tornillos hay en el panel?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("63 tornillos (7 × 9)", "56 tornillos", "16 tornillos", "72 tornillos"),
                            correctOptionIndex = 0,
                            explanation = "El número total en una cuadrícula es el producto de filas por columnas: 7 × 9 = 63 tornillos."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat3_u02",
            track = CurriculumTrack.MATEMATICA_3,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "División Exacta e Inexacta y Reparto Equitativo",
            description = "Concepto de dividendo, divisor, cociente y residuo en problemas de reparto.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat3_c_division",
                    conceptCode = "CR_MAT3_DIVISION",
                    title = "Algoritmo de la División y Reparto",
                    description = "Relación inversa entre multiplicación y división.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat3_reparto_tuercas",
                            conceptId = "cr_mat3_c_division",
                            title = "Reparto Equitativo de Bujías",
                            prompt = "Un taller recibe 36 bujías nuevas y debe distribuirlas en partes iguales entre 4 mecánicos. ¿Cuántas recibe cada uno?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("9 bujías (36 ÷ 4 = 9)", "8 bujías", "12 bujías", "6 bujías"),
                            correctOptionIndex = 0,
                            explanation = "36 dividido entre 4 es igual a 9 exacto, porque 9 × 4 = 36."
                        ),
                        InteractiveTaskData(
                            id = "task_mat3_transfer_residuo",
                            conceptId = "cr_mat3_c_division",
                            title = "Empaque de Llaveros con Sobrante",
                            prompt = "Se tienen 23 arandelas para empacar en bolsitas de 5 unidades. ¿Cuántas bolsitas llenas se obtienen y cuántas arandelas sobran?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("4 bolsitas y sobran 3 arandelas (23 = 5 × 4 + 3)", "5 bolsitas y no sobra nada", "3 bolsitas y sobran 8", "4 bolsitas y sobra 1"),
                            correctOptionIndex = 0,
                            explanation = "23 ÷ 5 = 4 con residuo 3. Se llenan 4 bolsas completas (20 unidades) y sobran 3."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat3_u03",
            track = CurriculumTrack.MATEMATICA_3,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Patrones Numéricos y Tablas de Doble Entrada",
            description = "Secuencias aritméticas crecientes y lectura de tablas de datos organizados.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat3_c_patrones",
                    conceptCode = "CR_MAT3_PATRONES",
                    title = "Secuencias Numéricas y Regularidades",
                    description = "Identificación del patrón aditivo y multiplicativo.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat3_secuencia_km",
                            conceptId = "cr_mat3_c_patrones",
                            title = "Registro de Kilómetros de Mantenimiento",
                            prompt = "El cambio de aceite de un vehículo se realiza en la secuencia: 5000 km, 10000 km, 15000 km... ¿Cuál es el siguiente servicio?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("20000 km (+5000 cada intervalo)", "18000 km", "25000 km", "16000 km"),
                            correctOptionIndex = 0,
                            explanation = "El patrón añade 5000 km en cada paso: 15000 + 5000 = 20000 km."
                        ),
                        InteractiveTaskData(
                            id = "task_mat3_transfer_tabla",
                            conceptId = "cr_mat3_c_patrones",
                            title = "Lectura de Tabla de Mantenimiento",
                            prompt = "Una tabla indica: 1 llanta = ₡25.000, 2 llantas = ₡50.000, 3 llantas = ₡75.000. ¿Cuánto costarán 4 llantas?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("₡100.000 (4 × 25.000)", "₡80.000", "₡125.000", "₡90.000"),
                            correctOptionIndex = 0,
                            explanation = "El costo unitario es ₡25.000; para 4 unidades: 4 × 25.000 = ₡100.000."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat3_u04",
            track = CurriculumTrack.MATEMATICA_3,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Medición del Tiempo (Reloj Analógico, Horas y Minutos)",
            description = "Lectura del reloj, intervalos de tiempo y conversión de horas a minutos.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat3_c_tiempo",
                    conceptCode = "CR_MAT3_TIEMPO",
                    title = "El Reloj y Unidades de Tiempo",
                    description = "Cálculo de duración de tareas en horas y minutos.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat3_minutos_hora",
                            conceptId = "cr_mat3_c_tiempo",
                            title = "Minutos en una Hora",
                            prompt = "¿Cuántos minutos tiene una hora completa y cuántos tiene media hora?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("60 minutos y 30 minutos", "100 minutos y 50 minutos", "24 minutos y 12 minutos", "60 minutos y 15 minutos"),
                            correctOptionIndex = 0,
                            explanation = "1 hora = 60 minutos, y la mitad de 60 es 30 minutos."
                        ),
                        InteractiveTaskData(
                            id = "task_mat3_transfer_duracion_servicio",
                            conceptId = "cr_mat3_c_tiempo",
                            title = "Cálculo de Entrega del Vehículo",
                            prompt = "Un cambio de frenos inicia a las 9:15 a.m. y tarda 1 hora y 45 minutos. ¿A qué hora finaliza el trabajo?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("11:00 a.m. (9:15 + 1h = 10:15; 10:15 + 45 min = 11:00)", "10:45 a.m.", "11:15 a.m.", "10:30 a.m."),
                            correctOptionIndex = 0,
                            explanation = "9:15 más 1 hora son las 10:15. Sumando 45 minutos: 15 + 45 = 60 minutos = 1 hora adicional, dando las 11:00 a.m."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 4.º AÑO: MATEMÁTICA
    // ══════════════════════════════════════════════════════════════════════════
    val MATEMATICA_4_UNITS = listOf(
        CourseUnitData(
            id = "cr_mat4_u01",
            track = CurriculumTrack.MATEMATICA_4,
            unitNumber = 1,
            targetMonth = 3,
            monthName = "Marzo",
            title = "Fracciones Propias, Impropias y Homogéneas",
            description = "Representación gráfica, numeradores, denominadores y suma de fracciones de igual base.",
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
                        ),
                        InteractiveTaskData(
                            id = "task_mat4_transfer_resta_frac",
                            conceptId = "cr_mat4_c_fracciones",
                            title = "Consumo de Combustible en Viaje",
                            prompt = "Un camión sale con 7/8 de tanque de diésel y llega a destino con 2/8 de tanque. ¿Qué fracción consumió?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("5/8 de tanque (7/8 - 2/8 = 5/8)", "9/8 de tanque", "5/16 de tanque", "3/8 de tanque"),
                            correctOptionIndex = 0,
                            explanation = "Restando numeradores con igual denominador: 7/8 - 2/8 = (7 - 2)/8 = 5/8 consumido."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat4_u02",
            track = CurriculumTrack.MATEMATICA_4,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Fracciones Heterogéneas y Fracciones Equivalentes",
            description = "Conversión a común denominador y simplificación de fracciones en medidas estándar.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat4_c_equivalentes",
                    conceptCode = "CR_MAT4_EQUIVALENTES",
                    title = "Equivalencia y Fracciones de Pulgada",
                    description = "Multiplicación cruzada y amplificación de fracciones en llaves mecánicas.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat4_llave_equivalente",
                            conceptId = "cr_mat4_c_equivalentes",
                            title = "Equivalencia en Llaves Mecánicas",
                            prompt = "¿Cuál de las siguientes fracciones es equivalente a 2/4 de pulgada?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("1/2 pulgada (dividiendo numerador y denominador por 2)", "3/4 pulgada", "1/4 pulgada", "4/2 pulgada"),
                            correctOptionIndex = 0,
                            explanation = "2/4 simplificado dividiendo entre 2 resulta en 1/2."
                        ),
                        InteractiveTaskData(
                            id = "task_mat4_transfer_suma_heterogenea",
                            conceptId = "cr_mat4_c_equivalentes",
                            title = "Suma de Espesores de Empaque",
                            prompt = "Se colocan dos arandelas juntas: una de 1/2 pulgada y otra de 1/4 de pulgada. ¿Cuál es el espesor total?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("3/4 de pulgada (2/4 + 1/4 = 3/4)", "2/6 de pulgada", "1/6 de pulgada", "5/4 de pulgada"),
                            correctOptionIndex = 0,
                            explanation = "1/2 es equivalente a 2/4. Sumando 2/4 + 1/4 = 3/4 de pulgada."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat4_u03",
            track = CurriculumTrack.MATEMATICA_4,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Perímetro y Área de Rectángulos y Triángulos",
            description = "Fórmulas de perímetro (suma de lados) y área de figuras geométricas planas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat4_c_perimetro_area",
                    conceptCode = "CR_MAT4_PERIMETRO_AREA",
                    title = "Cálculo de Perímetro y Área de Superficies",
                    description = "Aplicación de fórmulas P = 2(b+h) y A = b × h en espacios reales.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat4_perimetro_banco",
                            conceptId = "cr_mat4_c_perimetro_area",
                            title = "Perímetro de una Mesa de Trabajo",
                            prompt = "Un banco de trabajo rectangular mide 3 metros de largo por 1 metro de ancho. ¿Cuál es su perímetro total?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("8 metros (2 × 3 + 2 × 1 = 6 + 2)", "3 metros", "4 metros", "6 metros"),
                            correctOptionIndex = 0,
                            explanation = "El perímetro es la suma de los 4 lados: 3 + 1 + 3 + 1 = 8 metros."
                        ),
                        InteractiveTaskData(
                            id = "task_mat4_transfer_area_piso",
                            conceptId = "cr_mat4_c_perimetro_area",
                            title = "Área de Piso de Taller para Pintura Epóxica",
                            prompt = "Una bahía de alineación mide 6 metros de largo por 4 metros de ancho. ¿Cuántos metros cuadrados (m²) de pintura epóxica se requieren?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("24 m² (Área = base × altura = 6 × 4)", "20 m²", "10 m²", "48 m²"),
                            correctOptionIndex = 0,
                            explanation = "El área rectangular es el producto del largo por el ancho: 6 m × 4 m = 24 m²."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat4_u04",
            track = CurriculumTrack.MATEMATICA_4,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Números Decimales: Décimas, Centésimas y Operaciones",
            description = "Lectura, escritura, comparación y suma de números decimales en medidas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat4_c_decimales",
                    conceptCode = "CR_MAT4_DECIMALES",
                    title = "Valor Posicional Decimal en Metrología",
                    description = "Comprender décimas (0.1) y centésimas (0.01) en calibraciones.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat4_suma_decimales",
                            conceptId = "cr_mat4_c_decimales",
                            title = "Suma de Espesores de Calibrador",
                            prompt = "¿Cuál es el resultado de sumar 4.25 mm y 1.50 mm?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("5.75 mm", "5.30 mm", "5.70 mm", "4.75 mm"),
                            correctOptionIndex = 0,
                            explanation = "Alineando la coma decimal: 4.25 + 1.50 = 5.75 mm."
                        ),
                        InteractiveTaskData(
                            id = "task_mat4_transfer_desgaste_disco",
                            conceptId = "cr_mat4_c_decimales",
                            title = "Espesor Mínimo del Disco de Freno",
                            prompt = "Un disco de freno mide 22.40 mm nuevo. El fabricante exige cambiarlo si su grosor baja de 20.00 mm. Si se mide y marca 19.85 mm, ¿qué procede?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Reemplazo inmediato, porque 19.85 mm es menor que el límite de 20.00 mm",
                                "No hacer nada, porque 19.85 mm es mayor que 20.00 mm",
                                "Lijar el disco para que mida más",
                                "Agregar más líquido de frenos"
                            ),
                            correctOptionIndex = 0,
                            explanation = "19.85 < 20.00 mm, por lo que el disco ha superado el límite de seguridad y debe reemplazarse."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 5.º AÑO: MATEMÁTICA
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_mat5_costo_proporcional",
                            conceptId = "cr_mat5_c_proporcionalidad",
                            title = "Cálculo de Aceite a Granel",
                            prompt = "Si 1 litro de aceite para motor cuesta ₡6.000, ¿cuánto cuestan 4.5 litros?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("₡27.000 (4.5 × 6.000)", "₡24.000", "₡30.000", "₡26.500"),
                            correctOptionIndex = 0,
                            explanation = "Multiplicando directamente: 4.5 × 6000 = ₡27.000."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat5_u02",
            track = CurriculumTrack.MATEMATICA_5,
            unitNumber = 2,
            targetMonth = 6,
            monthName = "Junio",
            title = "Operaciones Combinadas y Jerarquía de Operaciones",
            description = "Paréntesis, multiplicación, división, adición y sustracción combinadas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat5_c_jerarquia",
                    conceptCode = "CR_MAT5_JERARQUIA",
                    title = "Jerarquía de Operaciones Matemáticas",
                    description = "Prioridad de cálculo: paréntesis primero, luego multiplicación/división, finalmente suma/resta.",
                    targetMonth = 6,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat5_jerarquia_calc",
                            conceptId = "cr_mat5_c_jerarquia",
                            title = "Resolución con Prioridad de Operadores",
                            prompt = "¿Cuál es el resultado correcto de calcular 10 + 5 × 4 - 6 ÷ 2?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("27 (10 + 20 - 3 = 27)", "57", "23", "30"),
                            correctOptionIndex = 0,
                            explanation = "Primero multiplicaciones y divisiones: 5 × 4 = 20 y 6 ÷ 2 = 3. Luego sumas y restas: 10 + 20 - 3 = 27."
                        ),
                        InteractiveTaskData(
                            id = "task_mat5_transfer_cotizacion_repuestos",
                            conceptId = "cr_mat5_c_jerarquia",
                            title = "Cálculo de Factura de Taller con Mano de Obra",
                            prompt = "Una factura incluye: ₡15.000 de diagnóstico base + 3 horas de mano de obra a ₡12.000 cada una - ₡5.000 de descuento. ¿Cuál es el total?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("₡46.000 (15.000 + 3 × 12.000 - 5.000 = 15.000 + 36.000 - 5.000)", "₡49.000", "₡41.000", "₡56.000"),
                            correctOptionIndex = 0,
                            explanation = "15.000 + (3 × 12.000) - 5.000 = 15.000 + 36.000 - 5.000 = ₡46.000."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat5_u03",
            track = CurriculumTrack.MATEMATICA_5,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Clasificación de Ángulos y Propiedades de Triángulos",
            description = "Ángulos rectos (90°), agudos (<90°), obtusos (>90°) y suma de ángulos internos (180°).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat5_c_angulos",
                    conceptCode = "CR_MAT5_ANGULOS",
                    title = "Ángulos y Geometría Triangular",
                    description = "Medición con transportador y teorema de suma de ángulos internos.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat5_suma_angulos_triangulo",
                            conceptId = "cr_mat5_c_angulos",
                            title = "Suma de Ángulos Internos de un Triángulo",
                            prompt = "¿Cuánto suman siempre los tres ángulos internos de cualquier triángulo plano?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("180 grados", "360 grados", "90 grados", "270 grados"),
                            correctOptionIndex = 0,
                            explanation = "En la geometría euclidiana, la suma de los tres ángulos interiores de cualquier triángulo es exactamente 180°."
                        ),
                        InteractiveTaskData(
                            id = "task_mat5_transfer_alineacion_chasis",
                            conceptId = "cr_mat5_c_angulos",
                            title = "Ángulo de Caída (Camber) en Suspensión",
                            prompt = "En la suspensión de un auto, la rueda respecto a la vertical forma un ángulo agudo de 1.5°. Si un ángulo recto mide 90°, ¿qué tipo de ángulo es 1.5°?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("Ángulo agudo (estrictamente mayor que 0° y menor que 90°)", "Ángulo obtuso", "Ángulo llano", "Ángulo nulo"),
                            correctOptionIndex = 0,
                            explanation = "Cualquier ángulo entre 0° y 90° se clasifica como ángulo agudo."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat5_u04",
            track = CurriculumTrack.MATEMATICA_5,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Estadística Básica: Media, Mediana y Gráficos de Barras",
            description = "Cálculo del promedio aritmético e interpretación de tendencias en datos.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat5_c_estadistica",
                    conceptCode = "CR_MAT5_ESTADISTICA",
                    title = "Media Aritmética y Análisis de Frecuencias",
                    description = "Cálculo de la media sumando los valores y dividiendo entre la cantidad total.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat5_calculo_promedio",
                            conceptId = "cr_mat5_c_estadistica",
                            title = "Promedio de Calificaciones",
                            prompt = "Un estudiante obtiene notas de 80, 90 y 100 en tres exámenes. ¿Cuál es su promedio (media aritmética)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("90 ((80 + 90 + 100) ÷ 3 = 270 ÷ 3 = 90)", "85", "95", "80"),
                            correctOptionIndex = 0,
                            explanation = "La suma es 80 + 90 + 100 = 270. Dividido entre 3 notas da 90."
                        ),
                        InteractiveTaskData(
                            id = "task_mat5_transfer_consumo_flota",
                            conceptId = "cr_mat5_c_estadistica",
                            title = "Consumo Promedio de Combustible en Flota",
                            prompt = "Cuatro repartidores reportaron un consumo semanal de 25 L, 30 L, 35 L y 30 L. ¿Cuál fue el consumo promedio por repartidor?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("30 litros ((25 + 30 + 35 + 30) ÷ 4 = 120 ÷ 4)", "28 litros", "32 litros", "35 litros"),
                            correctOptionIndex = 0,
                            explanation = "Total = 120 litros entre 4 vehículos = 30 litros promedio."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 6.º AÑO: MATEMÁTICA
    // ══════════════════════════════════════════════════════════════════════════
    val MATEMATICA_6_UNITS = listOf(
        CourseUnitData(
            id = "cr_mat6_u01",
            track = CurriculumTrack.MATEMATICA_6,
            unitNumber = 1,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Porcentajes y Cálculo del IVA (13% en Costa Rica)",
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
                            options = listOf("₡2.600 (20.000 × 0.13)", "₡1.300", "₡2.000", "₡3.000"),
                            correctOptionIndex = 0,
                            explanation = "20.000 × 0.13 = ₡2.600."
                        ),
                        InteractiveTaskData(
                            id = "task_mat6_transfer_descuento_taller",
                            conceptId = "cr_mat6_c_porcentajes",
                            title = "Descuento Comercial de Taller",
                            prompt = "Un juego de llantas cuesta ₡160.000. Por promoción ofrecen un 20% de descuento. ¿Cuál es el precio final a pagar antes de impuestos?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("₡128.000 (Descuento = 32.000; 160.000 - 32.000 = 128.000)", "₡140.000", "₡130.000", "₡132.000"),
                            correctOptionIndex = 0,
                            explanation = "El 20% de 160.000 es 160.000 × 0.20 = ₡32.000. Restando: 160.000 - 32.000 = ₡128.000."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat6_u02",
            track = CurriculumTrack.MATEMATICA_6,
            unitNumber = 2,
            targetMonth = 7,
            monthName = "Julio",
            title = "Números Enteros Básicos y Escalas Térmicas",
            description = "Concepto de números positivos, negativos y el cero en la recta numérica.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat6_c_enteros_basicos",
                    conceptCode = "CR_MAT6_ENTEROS_BASICOS",
                    title = "Introducción a los Números Negativos y la Recta Numérica",
                    description = "Uso de números relativos para temperaturas bajo cero, balances y presiones.",
                    targetMonth = 7,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat6_temperatura_congelador",
                            conceptId = "cr_mat6_c_enteros_basicos",
                            title = "Temperatura Bajo Cero",
                            prompt = "Si un refrigerador de taller está a 4 °C y baja 7 grados durante la noche, ¿cuál es la temperatura final?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("-3 °C (4 - 7 = -3)", "-4 °C", "3 °C", "-11 °C"),
                            correctOptionIndex = 0,
                            explanation = "Restando 7 de 4 en la recta numérica se pasa por cero hasta -3 °C."
                        ),
                        InteractiveTaskData(
                            id = "task_mat6_transfer_vacio_manometro",
                            conceptId = "cr_mat6_c_enteros_basicos",
                            title = "Lectura de Vacío en Aire Acondicionado",
                            prompt = "Al realizar vacío en el circuito de climatización automotriz, la aguja pasa de 0 psi a -29 inHg (pulgadas de mercurio). ¿Qué indica el signo negativo?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Una presión inferior a la presión atmosférica ambiente (vacío relativo)",
                                "Una sobrepresión con riesgo de explosión",
                                "Que el equipo está apagado",
                                "Una temperatura extrema de ebullición"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El signo negativo en manómetros de presión relativa indica presión inferior a la atmosférica (succión/vacío)."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat6_u03",
            track = CurriculumTrack.MATEMATICA_6,
            unitNumber = 3,
            targetMonth = 9,
            monthName = "Septiembre",
            title = "Volumen de Prismas Rectangulares y Cilindros",
            description = "Fórmulas de volumen (V = Área basal × Altura) en recipientes, motores y tanques.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat6_c_volumen",
                    conceptCode = "CR_MAT6_VOLUMEN",
                    title = "Cálculo de Volumen y Capacidad en Litros",
                    description = "Relación entre centímetros cúbicos (cm³), decímetros cúbicos (dm³) y litros (1 dm³ = 1 L).",
                    targetMonth = 9,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat6_volumen_prisma",
                            conceptId = "cr_mat6_c_volumen",
                            title = "Volumen de una Caja de Herramientas",
                            prompt = "Una gaveta metálica mide 50 cm de largo, 30 cm de ancho y 10 cm de alto. ¿Cuál es su volumen en cm³?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("15.000 cm³ (50 × 30 × 10)", "1.500 cm³", "90 cm³", "150.000 cm³"),
                            correctOptionIndex = 0,
                            explanation = "Volumen = largo × ancho × alto = 50 × 30 × 10 = 15.000 cm³."
                        ),
                        InteractiveTaskData(
                            id = "task_mat6_transfer_cilindrada_cc",
                            conceptId = "cr_mat6_c_volumen",
                            title = "Equivalencia de Centímetros Cúbicos a Litros",
                            prompt = "Un motor tiene una cilindrada total de 2000 centímetros cúbicos (cm³ o cc). ¿A cuántos litros equivale?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("2.0 litros (1000 cc = 1 L)", "20 litros", "0.2 litros", "200 litros"),
                            correctOptionIndex = 0,
                            explanation = "Dado que 1000 cm³ equivalen exactamente a 1 litro, 2000 cm³ equivalen a 2.0 litros."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_mat6_u04",
            track = CurriculumTrack.MATEMATICA_6,
            unitNumber = 4,
            targetMonth = 11,
            monthName = "Noviembre",
            title = "Probabilidad Simple y Regla de Laplace",
            description = "Cálculo de probabilidades simples: casos favorables divididos entre casos posibles.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_mat6_c_probabilidad",
                    conceptCode = "CR_MAT6_PROBABILIDAD",
                    title = "Probabilidad Teórica y Toma de Decisiones",
                    description = "Fracciones y porcentajes de probabilidad en eventos aleatorios.",
                    targetMonth = 11,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_mat6_probabilidad_dado",
                            conceptId = "cr_mat6_c_probabilidad",
                            title = "Probabilidad en Lanzamiento de Dado",
                            prompt = "Al lanzar un dado común de 6 caras numeradas del 1 al 6, ¿cuál es la probabilidad de obtener un número par (2, 4 o 6)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("3/6 = 1/2 = 50%", "1/6 = 16.6%", "2/6 = 33.3%", "4/6 = 66.6%"),
                            correctOptionIndex = 0,
                            explanation = "Hay 3 casos favorables (2, 4, 6) entre 6 casos posibles: 3/6 = 1/2 = 50%."
                        ),
                        InteractiveTaskData(
                            id = "task_mat6_transfer_control_calidad",
                            conceptId = "cr_mat6_c_probabilidad",
                            title = "Probabilidad en Lote de Calidad de Sensores",
                            prompt = "En una caja de 100 sensores nuevos, el control de calidad detectó que 5 salieron defectuosos. Si un mecánico toma uno al azar, ¿cuál es la probabilidad de que funcione bien?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf("95% (95 de 100 están en buen estado)", "5%", "50%", "90%"),
                            correctOptionIndex = 0,
                            explanation = "Si 5 de 100 son defectuosos, 95 están buenos: 95/100 = 0.95 = 95% de probabilidad de éxito."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // CIENCIAS PRIMARIA
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_cien_pri_transfer_impacto_ambiental",
                            conceptId = "cr_cien_c_ecosistemas",
                            title = "Impacto del Derrame de Hidrocarburos",
                            prompt = "Si se vierte aceite de motor usado directamente en un río o suelo, ¿qué ocurre con el ecosistema acuático?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El aceite forma una película que bloquea el oxígeno y la luz solar, asfixiando peces y algas",
                                "Nutre a los peces y aumenta las plantas",
                                "Se evapora sin dejar ningún residuo tóxico",
                                "Purifica el agua de bacterias"
                            ),
                            correctOptionIndex = 0,
                            explanation = "1 litro de aceite usado contamina 1 millón de litros de agua, creando una película impermeable al oxígeno."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cien_pri_u02",
            track = CurriculumTrack.CIENCIAS_PRIMARIA,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Estados de la Materia y Cambios de Fase",
            description = "Sólido, líquido, gaseoso, evaporación, condensación, fusión y solidificación.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cien_c_materia_estados",
                    conceptCode = "CR_CIEN_MATERIA_ESTADOS",
                    title = "Cambios Físicos de la Materia y Temperatura",
                    description = "Comportamiento cinético de las moléculas ante el calor.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cien_pri_ebullicion_agua",
                            conceptId = "cr_cien_c_materia_estados",
                            title = "Punto de Ebullición del Agua",
                            prompt = "¿A qué temperatura a nivel del mar el agua pura cambia de estado líquido a gaseoso (vapor)?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("100 °C", "0 °C", "50 °C", "200 °C"),
                            correctOptionIndex = 0,
                            explanation = "A 1 atmósfera de presión, el agua hierve y pasa a vapor a 100 °C."
                        ),
                        InteractiveTaskData(
                            id = "task_cien_pri_transfer_refrigerante_presion",
                            conceptId = "cr_cien_c_materia_estados",
                            title = "Ebullición del Líquido de Frenos (Vapor Lock)",
                            prompt = "Si el líquido de frenos absorbe humedad y se calienta en bajadas prolongadas, el agua hierve formando burbujas de vapor. ¿Por qué el pedal se va al fondo?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Porque los gases se comprimen fácilmente, mientras que los líquidos son prácticamente incompresibles",
                                "Porque el vapor se vuelve más pesado que el plomo",
                                "Porque las pastillas se congelan instantáneamente",
                                "Porque el pedal se desconecta electrónicamente"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Los líquidos transmiten presión hidráulica sin comprimirse; las burbujas de vapor se comprimen, perdiendo el frenado."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cien_pri_u03",
            track = CurriculumTrack.CIENCIAS_PRIMARIA,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "El Cuerpo Humano: Sistemas y Salud Ocupacional",
            description = "Sistemas circulatorio, respiratorio, digestivo y medidas de protección en el trabajo.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cien_c_sistemas_cuerpo",
                    conceptCode = "CR_CIEN_SISTEMAS_CUERPO",
                    title = "Fisiología y Cuidado de los Órganos Vitales",
                    description = "El corazón, pulmones y prevención de accidentes laborales.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cien_pri_oxigeno_pulmones",
                            conceptId = "cr_cien_c_sistemas_cuerpo",
                            title = "Función del Sistema Respiratorio",
                            prompt = "¿Cuál es el gas indispensable que los pulmones extraen del aire para llevarlo a la sangre?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Oxígeno (O₂)", "Dióxido de carbono (CO₂)", "Metano", "Monóxido de carbono"),
                            correctOptionIndex = 0,
                            explanation = "Los alvéolos pulmonares captan oxígeno (O₂) y expulsan dióxido de carbono (CO₂)."
                        ),
                        InteractiveTaskData(
                            id = "task_cien_pri_transfer_proteccion_pulmonar",
                            conceptId = "cr_cien_c_sistemas_cuerpo",
                            title = "Protección Respiratoria ante Pintura Automotriz",
                            prompt = "¿Por qué un pintor de autos debe usar respirador con filtros de carbón activado para vapores orgánicos?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Para evitar que los solventes tóxicos ingresen a los alvéolos y pasen al torrente sanguíneo",
                                "Para evitar que la pintura le manche la cara",
                                "Para respirar con mayor lentitud",
                                "Únicamente para cumplir con el uniforme"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Los vapores de solventes y poliuretanos causan intoxicación respiratoria severa y daño neurológico."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_cien_pri_u04",
            track = CurriculumTrack.CIENCIAS_PRIMARIA,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Energía y Sostenibilidad Ambiental en Costa Rica",
            description = "Energía hidroeléctrica, eólica, solar, geotérmica y gestión de residuos.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_cien_c_energia_cr",
                    conceptCode = "CR_CIEN_ENERGIA_CR",
                    title = "Matriz Eléctrica Renovable y Reciclaje",
                    description = "Aprovechamiento sostenible de los recursos naturales en Costa Rica.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_cien_pri_energia_renovable",
                            conceptId = "cr_cien_c_energia_cr",
                            title = "Fuentes de Energía Limpia en Costa Rica",
                            prompt = "¿Cuál es la principal fuente de generación eléctrica limpia en la red nacional de Costa Rica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Energía hidroeléctrica (fuerza del agua)", "Carbón mineral", "Energía nuclear", "Petróleo pesado"),
                            correctOptionIndex = 0,
                            explanation = "Costa Rica genera más del 70% de su electricidad mediante plantas hidroeléctricas del ICE."
                        ),
                        InteractiveTaskData(
                            id = "task_cien_pri_transfer_reciclaje_baterias",
                            conceptId = "cr_cien_c_energia_cr",
                            title = "Economía Circular y Baterías de Plomo",
                            prompt = "Las baterías de auto viejas contienen plomo y ácido sulfúrico. ¿Cuál es el procedimiento ambiental correcto para desecharlas?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Entregarlas a un centro autorizado para fundición de plomo y reciclaje certificado",
                                "Enterrarlas en el patio para que se degraden",
                                "Tirarlas a la basura común municipal",
                                "Vaciar el ácido en la alcantarilla"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El plomo es un metal pesado altamente tóxico; su reciclaje industrial en fundiciones autorizadas evita la contaminación del suelo."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // ESPAÑOL PRIMARIA
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_esp_pri_transfer_manual_tecnico",
                            conceptId = "cr_esp_c_comprension",
                            title = "Lectura Crítica de Advertencia en Manual",
                            prompt = "Un manual de compresor de aire dice: 'ADVERTENCIA: Despresurice el tanque antes de aflojar cualquier manguera'. ¿Cuál es la consecuencia inferida si se desobedece?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "La manguera puede salir disparada a alta velocidad por la presión acumulada y causar lesiones",
                                "El compresor gastará más aceite",
                                "La manguera se limpiará sola",
                                "El color de la máquina cambiará"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La advertencia alerta sobre el peligro de expulsión violenta por energía neumática contenida."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp_pri_u02",
            track = CurriculumTrack.ESPANOL_PRIMARIA,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Reglas de Acentuación: Agudas, Graves y Esdrújulas",
            description = "Clasificación de palabras según la sílaba tónica y reglas de la tilde en español.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp_c_acentuacion",
                    conceptCode = "CR_ESP_ACENTUACION",
                    title = "Acentuación y Ortografía Literal",
                    description = "Reglas para tildar agudas (n, s, vocal), graves y esdrújulas (siempre).",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp_pri_esdrujulas",
                            conceptId = "cr_esp_c_acentuacion",
                            title = "Regla de las Palabras Esdrújulas",
                            prompt = "¿Cuál es la regla fundamental de acentuación para todas las palabras esdrújulas?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Todas las palabras esdrújulas se tildan sin excepción (ej. mecánico, brújula)",
                                "Solo se tildan si terminan en vocal",
                                "Solo se tildan si terminan en consonante distinta de n o s",
                                "Nunca llevan tilde"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Las palabras esdrújulas tienen la fuerza en la antepenúltima sílaba y siempre llevan tilde ortográfica."
                        ),
                        InteractiveTaskData(
                            id = "task_esp_pri_transfer_ortografia_orden",
                            conceptId = "cr_esp_c_acentuacion",
                            title = "Ortografía en Órdenes de Trabajo",
                            prompt = "En una orden de servicio escrita por un mecánico, ¿cuál de las siguientes oraciones está escrita con ortografía y tildación impecables?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El vehículo presentó vibración en el motor y se cambió el pistón con éxito.",
                                "El vehiculo presento vibracion en el motor y se cambio el piston con exito.",
                                "El vihiculo tenia una bibrasion en el motór.",
                                "Se hiso canbio de aséite en el tayer."
                            ),
                            correctOptionIndex = 0,
                            explanation = "'Vehículo' (esdrújula), 'vibración' (aguda en n), 'pistón' (aguda en n) y 'éxito' (esdrújula) llevan tilde correcta."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp_pri_u03",
            track = CurriculumTrack.ESPANOL_PRIMARIA,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Tipología Textual: La Carta Formal y el Instructivo",
            description = "Estructura de la carta formal, encabezado, cuerpo, despedida y textos prescriptivos.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp_c_carta_instructivo",
                    conceptCode = "CR_ESP_CARTA_INSTRUCTIVO",
                    title = "Estructura y Registro Formal en la Redacción",
                    description = "Uso de conectores de orden y lenguaje respetuoso y preciso.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp_pri_partes_carta",
                            conceptId = "cr_esp_c_carta_instructivo",
                            title = "Partes de una Carta Formal",
                            prompt = "¿Cuál es el orden cronológico estándar de los elementos en una carta formal?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Lugar y fecha -> Destinatario -> Saludo -> Cuerpo del texto -> Despedida -> Firma",
                                "Firma -> Saludo -> Fecha -> Despedida",
                                "Cuerpo -> Fecha -> Firma -> Destinatario",
                                "Saludo -> Firma -> Lugar y fecha"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La estructura formal inicia con fecha y encabezado, desarrolla la solicitud y concluye con despedida y firma."
                        ),
                        InteractiveTaskData(
                            id = "task_esp_pri_transfer_reclamo_garantia",
                            conceptId = "cr_esp_c_carta_instructivo",
                            title = "Redacción Formal de Reclamo de Garantía",
                            prompt = "Al solicitar una garantía de repuesto a un distribuidor, ¿qué fórmula de saludo y registro de lenguaje es la apropiada?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "'Estimados señores del Departamento de Garantías: Por medio de la presente solicito formalmente la revisión...'",
                                "'Hola mae, el repuesto me salió malo, cámbiemelo rápido'",
                                "'Buenas, vengo a reclamar'",
                                "'Estimado amigo, ojalá me ayude con esto'"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Las comunicaciones comerciales y legales exigen registro formal, cortesía y términos precisos."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_esp_pri_u04",
            track = CurriculumTrack.ESPANOL_PRIMARIA,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Semántica: Sinónimos, Antónimos y Precisión Léxica",
            description = "Enriquecimiento del vocabulario, coherencia semántica y tecnicismos apropiados.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_esp_c_semantica",
                    conceptCode = "CR_ESP_SEMANTICA",
                    title = "Precisión Léxica y Uso del Diccionario",
                    description = "Reemplazar palabras comodín (cosa, algo) por sustantivos y verbos exactos.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_esp_pri_sinonimo_preciso",
                            conceptId = "cr_esp_c_semantica",
                            title = "Sinónimos y Precisión",
                            prompt = "En la frase 'El técnico reparó la máquina', ¿cuál palabra es un sinónimo preciso del verbo 'reparó'?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Subsanó o arregló", "Destruyó", "Compró", "Observó"),
                            correctOptionIndex = 0,
                            explanation = "Reparar y subsanar o arreglar comparten el significado de restaurar el funcionamiento correcto."
                        ),
                        InteractiveTaskData(
                            id = "task_esp_pri_transfer_tecnicismo",
                            conceptId = "cr_esp_c_semantica",
                            title = "Sustitución de Lenguaje Coloquial por Término Técnico",
                            prompt = "En lugar de decir coloquialmente 'se le quebró una cosita de hierro adentro al carro', ¿cuál redacción técnica demuestra nivel profesional?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "'Se constató la fractura mecánica de un perno de sujeción interno'",
                                "'Se le rompió un pedazo de lata'",
                                "'Un fierro se desarmó'",
                                "'El chunche de adentro no sirve'"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El uso de términos precisos ('fractura mecánica', 'perno de sujeción') otorga validez técnica y rigor pericial."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // ESTUDIOS SOCIALES PRIMARIA
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_soc_pri_transfer_carreta_trabajo",
                            conceptId = "cr_soc_pri_c_comunidad",
                            title = "La Carreta Típica como Símbolo del Trabajo",
                            prompt = "La Carreta Típica costarricense es Patrimonio de la Humanidad (UNESCO). Históricamente, ¿cuál fue su función socioeconómica principal?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El transporte de café desde el Valle Central hasta los puertos de Puntarenas y Limón para la exportación",
                                "Un vehículo militar de combate",
                                "Un juguete infantil",
                                "Un medio de transporte exclusivo de presidentes"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La carreta fue la columna vertebral del comercio cafetalero y la articulación económica de Costa Rica en el siglo XIX."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc_pri_u02",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_PRIMARIA,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Las 7 Provincias y Relieve Geográfico de Costa Rica",
            description = "San José, Alajuela, Cartago, Heredia, Guanacaste, Puntarenas y Limón: cordilleras y costas.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc_pri_c_provincias",
                    conceptCode = "CR_SOC_PRI_PROVINCIAS",
                    title = "División Territorial y Puertos Nacionales",
                    description = "Ubicación de cabeceras de provincia y conexiones de transporte interoceánico.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc_pri_puertos_costas",
                            conceptId = "cr_soc_pri_c_provincias",
                            title = "Puertos Marítimos de Costa Rica",
                            prompt = "¿Cuáles son las dos provincias costarricenses que tienen costa en el Océano Pacífico y el Mar Caribe respectivamente?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf(
                                "Puntarenas (Pacífico) y Limón (Caribe)",
                                "San José y Alajuela",
                                "Heredia y Cartago",
                                "Guanacaste y Heredia"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Puntarenas abarca la mayor extensión del litoral pacífico y Limón cubre todo el litoral caribeño."
                        ),
                        InteractiveTaskData(
                            id = "task_soc_pri_transfer_logistica_rutas",
                            conceptId = "cr_soc_pri_c_provincias",
                            title = "Logística de Transporte de Repuestos en Costa Rica",
                            prompt = "Los repuestos importados por vía marítima desde Asia ingresan por Puerto Caldera (Pacífico), mientras que los de Europa entran por Moín (Caribe). ¿Por qué ruta montañosa cruzan para llegar a San José?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Ruta 27 (desde el Pacífico) y Ruta 32 a través del Parque Nacional Braulio Carrillo (desde el Caribe)",
                                "Ruta Interamericana Norte únicamente",
                                "Por el Ferrocarril al Pacífico exclusivamente",
                                "Por túneles subterráneos bajo el mar"
                            ),
                            correctOptionIndex = 0,
                            explanation = "La Ruta 27 conecta el Pacífico Central y la Ruta 32 conecta Limón cruzando la Cordillera Volcánica Central."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc_pri_u03",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_PRIMARIA,
            unitNumber = 3,
            targetMonth = 7,
            monthName = "Julio",
            title = "La Anexión del Partido de Nicoya (1824)",
            description = "Incorporación pacífica y voluntaria de Guanacaste a Costa Rica.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc_pri_c_nicoya",
                    conceptCode = "CR_SOC_PRI_NICOYA",
                    title = "El Lema Patriótico: De la Patria por Nuestra Voluntad",
                    description = "Autonomía de los cabildos abiertos de Nicoya y Santa Cruz en 1824.",
                    targetMonth = 7,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc_pri_fecha_anexion",
                            conceptId = "cr_soc_pri_c_nicoya",
                            title = "Fecha Conmemorativa de la Anexión",
                            prompt = "¿Qué fecha patria se conmemora cada año en Costa Rica la Anexión del Partido de Nicoya?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("25 de julio de 1824", "15 de setiembre de 1821", "11 de abril de 1856", "1 de mayo de 1857"),
                            correctOptionIndex = 0,
                            explanation = "El 25 de julio de 1824 los habitantes del Partido de Nicoya decidieron unirse voluntariamente a Costa Rica."
                        ),
                        InteractiveTaskData(
                            id = "task_soc_pri_transfer_aporte_guanacaste",
                            conceptId = "cr_soc_pri_c_nicoya",
                            title = "Aportes Culturales y Económicos de Guanacaste",
                            prompt = "¿Cuáles son aportes fundamentales de Guanacaste a la identidad y economía de Costa Rica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "El folclore (música, punto guanacasteco, marimba), la gastronomía a base de maíz y la ganadería",
                                "La minería de diamantes y la industria automotriz",
                                "La producción de nieve y esquí",
                                "La flota de submarinos comerciales"
                            ),
                            correctOptionIndex = 0,
                            explanation = "Guanacaste enriqueció a la nación con la marimba, el árbol de Guayacán y Guanacaste, comidas criollas y riqueza agropecuaria."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_soc_pri_u04",
            track = CurriculumTrack.ESTUDIOS_SOCIALES_PRIMARIA,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Instituciones Públicas de Servicio y Seguridad Ciudadana",
            description = "El Cuerpo de Bomberos, Cruz Roja, Fuerza Pública, CCSS, ICE y AyA.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_soc_pri_c_instituciones",
                    conceptCode = "CR_SOC_PRI_INSTITUCIONES",
                    title = "Servicios de Emergencia y Solidaridad Social",
                    description = "El número único de emergencias 9-1-1 y la función de los cuerpos de socorro.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_soc_pri_linea_emergencia",
                            conceptId = "cr_soc_pri_c_instituciones",
                            title = "Línea Nacional de Emergencias",
                            prompt = "¿Cuál es el número telefónico gratuito y universal para reportar emergencias vitales en Costa Rica?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("9-1-1", "1-1-2", "9-9-9", "1-1-5"),
                            correctOptionIndex = 0,
                            explanation = "El sistema 9-1-1 centraliza el despacho inmediato de Cruz Roja, Bomberos, Tránsito y Fuerza Pública."
                        ),
                        InteractiveTaskData(
                            id = "task_soc_pri_transfer_protocolo_fuego_taller",
                            conceptId = "cr_soc_pri_c_instituciones",
                            title = "Protocolo ante Incendio con Combustibles en Taller",
                            prompt = "Si ocurre un amago de fuego con gasolina en un taller, ¿a qué institución se llama inmediatamente y qué tipo de extintor se utiliza?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Llamar al 9-1-1 (Bomberos de Costa Rica) y usar extintor de Polvo Químico Seco ABC (no agua)",
                                "Echar baldes de agua corriente sobre la gasolina encendida",
                                "Esperar a que se apague solo",
                                "Cerrar la puerta con candado"
                            ),
                            correctOptionIndex = 0,
                            explanation = "El agua esparce la gasolina flotante propagando el fuego; se requiere polvo químico ABC y llamar a Bomberos (9-1-1)."
                        )
                    )
                )
            )
        )
    )

    // ══════════════════════════════════════════════════════════════════════════
    // INGLÉS PRIMARIA
    // ══════════════════════════════════════════════════════════════════════════
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
                        ),
                        InteractiveTaskData(
                            id = "task_ing_pri_numbers_counting",
                            conceptId = "cr_ing_pri_c_basics",
                            title = "Counting Tools in English",
                            prompt = "Count the items: 'One, two, three, four, five...'. How do you say 12 in English?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Twelve", "Twenty", "Two", "Ten"),
                            correctOptionIndex = 0,
                            explanation = "The number 12 is written and pronounced 'Twelve' in English."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing_pri_u02",
            track = CurriculumTrack.INGLES_PRIMARIA,
            unitNumber = 2,
            targetMonth = 5,
            monthName = "Mayo",
            title = "Classroom Objects, Actions & Imperatives",
            description = "Commands (open, close, listen, write), tools, and daily school objects.",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing_pri_c_commands",
                    conceptCode = "CR_ING_PRI_COMMANDS",
                    title = "Imperatives and Workshop Actions",
                    description = "Understanding direct commands in manuals and instructions.",
                    targetMonth = 5,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing_pri_imperatives_action",
                            conceptId = "cr_ing_pri_c_commands",
                            title = "Basic Action Verbs",
                            prompt = "What does the English command 'Open the door' mean in Spanish?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Abra la puerta", "Cierre la puerta", "Pinte la puerta", "Limpie el piso"),
                            correctOptionIndex = 0,
                            explanation = "'Open' significa abrir y 'door' es puerta."
                        ),
                        InteractiveTaskData(
                            id = "task_ing_pri_transfer_switch_command",
                            conceptId = "cr_ing_pri_c_commands",
                            title = "Technical Switch Label Reading",
                            prompt = "A machine switch clearly says: 'TURN OFF BEFORE OPENING'. What must the operator do?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Apagar el equipo antes de abrir la compuerta de seguridad",
                                "Encender el equipo con la compuerta abierta",
                                "Acelerar la máquina al máximo",
                                "Llamar por teléfono"
                            ),
                            correctOptionIndex = 0,
                            explanation = "'Turn off' significa apagar y 'before opening' significa antes de abrir."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing_pri_u03",
            track = CurriculumTrack.INGLES_PRIMARIA,
            unitNumber = 3,
            targetMonth = 8,
            monthName = "Agosto",
            title = "Body Parts, Personal Care & Protective Gear",
            description = "Eyes, hands, feet, ears, head and personal protective equipment (PPE).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing_pri_c_body_ppe",
                    conceptCode = "CR_ING_PRI_BODY_PPE",
                    title = "Safety Gear and Body Parts",
                    description = "Matching protective gear to human anatomy in workplace English.",
                    targetMonth = 8,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing_pri_matching_gloves",
                            conceptId = "cr_ing_pri_c_body_ppe",
                            title = "Protective Gear Matching",
                            prompt = "What protective equipment is used to protect your HANDS when working with hot engines?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("Gloves (Guantes)", "Boots (Botas)", "Helmet (Casco)", "Earplugs (Tapones de oídos)"),
                            correctOptionIndex = 0,
                            explanation = "'Gloves' protect hands from burns, cuts, and abrasive surfaces."
                        ),
                        InteractiveTaskData(
                            id = "task_ing_pri_transfer_eye_protection",
                            conceptId = "cr_ing_pri_c_body_ppe",
                            title = "Safety Sign Reading: Eye Protection",
                            prompt = "A workshop sign reads: 'WEAR SAFETY GOGGLES IN THIS AREA'. Which body part does this protect?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Eyes (Ojos), against flying metal debris and sparks",
                                "Feet (Pies)",
                                "Hands (Manos)",
                                "Ears (Oídos)"
                            ),
                            correctOptionIndex = 0,
                            explanation = "'Safety goggles' are protective glasses designed specifically to safeguard the eyes."
                        )
                    )
                )
            )
        ),
        CourseUnitData(
            id = "cr_ing_pri_u04",
            track = CurriculumTrack.INGLES_PRIMARIA,
            unitNumber = 4,
            targetMonth = 10,
            monthName = "Octubre",
            title = "Transportation, Vehicles & Safety Words",
            description = "Cars, buses, bicycles, trains, traffic lights and warning signs (Pre-A1/A1).",
            estimatedLessons = 8,
            concepts = listOf(
                CurriculumConceptData(
                    id = "cr_ing_pri_c_vehicles",
                    conceptCode = "CR_ING_PRI_VEHICLES",
                    title = "Vehicles and Road Vocabulary",
                    description = "Naming different means of transportation and basic road rules.",
                    targetMonth = 10,
                    tasks = listOf(
                        InteractiveTaskData(
                            id = "task_ing_pri_traffic_light",
                            conceptId = "cr_ing_pri_c_vehicles",
                            title = "Traffic Light Colors Meaning",
                            prompt = "What does the GREEN light on a traffic signal indicate to drivers?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = false,
                            options = listOf("GO (Avanzar con precaución)", "STOP (Detenerse por completo)", "PARK (Estacionar)", "REVERSE (Dar marcha atrás)"),
                            correctOptionIndex = 0,
                            explanation = "Green signifies permission to proceed ('Go')."
                        ),
                        InteractiveTaskData(
                            id = "task_ing_pri_transfer_dashboard_alert",
                            conceptId = "cr_ing_pri_c_vehicles",
                            title = "Dashboard English Warning Reading",
                            prompt = "If a vehicle dashboard displays: 'BRAKE FAILURE - STOP VEHICLE SAFELY', what is the critical action?",
                            type = TaskType.MULTIPLE_CHOICE,
                            isTransferTask = true,
                            options = listOf(
                                "Fallo en frenos: detener el vehículo de forma segura inmediatamente",
                                "Acelerar más rápido",
                                "Encender la radio",
                                "Continuar el viaje sin frenar"
                            ),
                            correctOptionIndex = 0,
                            explanation = "'Brake failure' significa fallo en frenos y 'stop vehicle safely' ordena detener el auto con seguridad."
                        )
                    )
                )
            )
        )
    )
}
