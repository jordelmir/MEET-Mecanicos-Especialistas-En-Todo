package com.elysium369.meet.education.data

data class SocraticHintTier(
    val tierLevel: Int, // 1 = Orientativa / Pista Leve, 2 = Principio Fundamental, 3 = Analogía o Ejemplo Isomorfo
    val title: String,
    val socraticQuestion: String,
    val conceptualScaffold: String,
)

data class CognitiveMisconceptionDetail(
    val code: String,
    val title: String,
    val studentFaultyAssumption: String,
    val counterExample: String,
    val socraticRemediationPrompt: String,
)

data class ConceptDeepKnowledge(
    val conceptId: String,
    val coreIntuition: String,
    val expertMentalModel: List<String>, // Step-by-step thinking protocol of an expert
    val misconceptions: List<CognitiveMisconceptionDetail>,
    val socraticHintTiers: List<SocraticHintTier>,
    val realWorldApplication: String,
    val vocationalEngineeringBridge: String? = null,
    val reflectionPrompt: String,
)

object NationalCurriculumDeepKnowledge {

    private val REGISTRY = mapOf(
        // ── 1.º AÑO: POSICIONES ESPACIALES ──────────────────────────────────────
        "cr_mat1_c_spatial_pos" to ConceptDeepKnowledge(
            conceptId = "cr_mat1_c_spatial_pos",
            coreIntuition = "El espacio físico se organiza mediante puntos de referencia fijos. Decir 'delante' o 'detrás' no tiene sentido sin definir primero respecto a qué objeto o persona nos estamos ubicando.",
            expertMentalModel = listOf(
                "1. Identifica el objeto de referencia (el punto cero o ancla visual).",
                "2. Determina el frente y el dorso natural del objeto de referencia.",
                "3. Traza una línea mental que divida la zona frontal de la posterior.",
                "4. Observa la posición del objetivo respecto a esa línea divisoria."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_REVERSED_DELANTE_DETRAS",
                    title = "Inversión de perspectiva relativa",
                    studentFaultyAssumption = "El estudiante asume que el 'frente' del objeto es el lado que da hacia sus propios ojos como observador externo.",
                    counterExample = "Si te paras detrás de un automóvil, tú ves el baúl de frente hacia ti, pero el automóvil está mirando hacia adelante en dirección a la carretera.",
                    socraticRemediationPrompt = "Imagina que tú eres la casita: ¿hacia dónde apuntaría tu puerta principal? Si el gato está en la pared ciega de atrás, ¿está delante o detrás de ti?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Punto de Referencia",
                    socraticQuestion = "¿Cuál es el objeto principal que sirve de referencia en la imagen?",
                    conceptualScaffold = "Busca primero la puerta o frente de la casita."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Línea Visual",
                    socraticQuestion = "¿El gato está en la zona donde se entra a la casita o en la pared posterior?",
                    conceptualScaffold = "Si la puerta marca el frente, la pared opuesta marca la parte trasera."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Analogía del Taller",
                    socraticQuestion = "Si estás trabajando en el motor de un vehículo, ¿el radiador está delante o detrás del bloque del motor?",
                    conceptualScaffold = "El radiador recibe el aire de frente; la caja de cambios está detrás."
                )
            ),
            realWorldApplication = "Navegación espacial, lectura de planos mecánicos, posicionamiento de sensores de reversa y estacionamiento.",
            vocationalEngineeringBridge = "ISCO 7231: Montaje y orientación de sensores de proximidad ultrasónicos y cámaras de reversa automotrices.",
            reflectionPrompt = "¿Cómo cambiaría la ubicación del gato si la casita girara 180 grados?"
        ),

        // ── 1.º AÑO: MONEDA Y ECONOMÍA COSTARRICENSE ────────────────────────────
        "cr_mat1_c_currency_crc" to ConceptDeepKnowledge(
            conceptId = "cr_mat1_c_currency_crc",
            coreIntuition = "El dinero costarricense es un sistema aditivo en base decimal. Las monedas de diferente denominación (₡5, ₡10, ₡25, ₡50, ₡100, ₡500) representan paquetes de valor que se combinan para alcanzar un total exacto.",
            expertMentalModel = listOf(
                "1. Identifica el monto total objetivo a pagar.",
                "2. Comienza agrupando las monedas de mayor denominación que no sobrepasen el total.",
                "3. Calcula la diferencia restante.",
                "4. Agrega denominaciones menores hasta que la suma coincida exactamente con el precio."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_CURRENCY_AMOUNT_MISMATCH",
                    title = "Confusión de denominación o sobrepago no exacto",
                    studentFaultyAssumption = "El estudiante confunde el conteo de unidades de monedas físicas con el valor monetario de cada pieza.",
                    counterExample = "Tener 5 monedas de ₡10 suma ₡50, mientras que tener 1 sola moneda de ₡100 vale el doble aunque sea solo un disco de metal.",
                    socraticRemediationPrompt = "¿Cuántos colones vale cada moneda que acabas de agregar? Sumemos de 100 en 100 primero y luego agreguemos las fracciones menores."
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Objetivo Financiero",
                    socraticQuestion = "¿Cuánto cuesta exactamente el jugo en la pulpería?",
                    conceptualScaffold = "El precio es ₡375. Necesitas alcanzar exactamente esa cifra."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Estrategia de Mayor Denominación",
                    socraticQuestion = "¿Cuántas monedas de ₡100 puedes usar sin pasarte de ₡375?",
                    conceptualScaffold = "3 monedas de ₡100 dan ₡300. Te faltan ₡75."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Completar la Fracción",
                    socraticQuestion = "¿Cómo formas ₡75 usando monedas de ₡50 y ₡25?",
                    conceptualScaffold = "₡50 + ₡25 = ₡75. Combinado con ₡300 obtienes exactamente ₡375."
                )
            ),
            realWorldApplication = "Transacciones en pulperías, cálculo de vueltos, presupuestos familiares y comercio diario en Costa Rica.",
            vocationalEngineeringBridge = "Facturación de compras de repuestos, cálculo de costos de mano de obra y cotizaciones de servicios.",
            reflectionPrompt = "¿Cuál sería la menor cantidad posible de monedas físicas para pagar ₡375?"
        ),

        // ── 7.º AÑO: ARTES INDUSTRIALES (FONTANERÍA Y PVC) ──────────────────────
        "cr_font7_c_pvc_joinery" to ConceptDeepKnowledge(
            conceptId = "cr_font7_c_pvc_joinery",
            coreIntuition = "La unión de tuberías de PVC no es un simple pegado con pegamento: es una soldadura química en frío. El solvente funde superficialmente el policloruro de vinilo para que ambas piezas se fusionen a nivel molecular.",
            expertMentalModel = listOf(
                "1. Corte recto a 90° con sierra o cortatubo y biselado exterior a 15°.",
                "2. Limpieza química con limpiador desengrasante para abrir los poros del polímero.",
                "3. Aplicación uniforme y rápida de cemento solvente en espiga y campana.",
                "4. Inserción con un cuarto de vuelta para distribuir el cemento.",
                "5. Sostenimiento firme durante 15 a 30 segundos para evitar que la unión se expulse sola por presión de gases."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_PVC_IMMEDIATE_RELEASE",
                    title = "Liberación inmediata sin retención mecánica",
                    studentFaultyAssumption = "El estudiante asume que al meter el tubo en el accesorio ya queda fijo al instante.",
                    counterExample = "Por la conicidad interna del accesorio y la presión de vapores solventes, el tubo tiende a retroceder 2 a 5 milímetros si se suelta antes de 15 segundos, causando fugas de agua invisibles.",
                    socraticRemediationPrompt = "Cuando empujas el tubo dentro del codo recién untado con cemento: ¿sientes una fuerza que intenta empujarlo hacia afuera? ¿Por qué es vital sostenerlo con las manos?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Efecto Químico",
                    socraticQuestion = "¿Qué le ocurre al plástico PVC cuando entra en contacto con el cemento solvente?",
                    conceptualScaffold = "El solvente ablanda el material y genera presión de expansión."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Tiempo de Inmovilización",
                    socraticQuestion = "¿Cuánto tiempo requiere la masa fundida para estabilizarse antes de soltar la fuerza de empuje?",
                    conceptualScaffold = "La norma técnica exige entre 15 y 30 segundos de sujeción ininterrumpida."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Consecuencia Práctica",
                    socraticQuestion = "Si sueltas el tubo a los 2 segundos, ¿qué le ocurrirá a la profundidad de inserción?",
                    conceptualScaffold = "Se deslizará hacia afuera y la unión perderá el 50% de su resistencia hidráulica."
                )
            ),
            realWorldApplication = "Instalación de redes de agua potable residencial, sistemas de riego tecnificado y líneas de desagüe sanitario.",
            vocationalEngineeringBridge = "ISCO 7126: Fontaneros y montadores de tuberías. Cumplimiento del Código de Instalaciones Hidráulicas y Sanitarias del CFIA.",
            reflectionPrompt = "¿Por qué debe limpiarse el exceso de cemento en el exterior de la unión con un trapo seco inmediatamente después de soldar?"
        ),

        // ── 8.º AÑO: ÁLGEBRA Y POLINOMIOS (UJARRÁS) ─────────────────────────────
        "cr_mat8_c_algebra" to ConceptDeepKnowledge(
            conceptId = "cr_mat8_c_algebra",
            coreIntuition = "El álgebra no es calcular números desconocidos mágicamente; es la generalización de la aritmética. Dos monomios solo se pueden sumar o restar si son semejantes (mismas variables con exactamente los mismos exponentes).",
            expertMentalModel = listOf(
                "1. Escanea cada término de la expresión polinómica.",
                "2. Aísla el factor literal (variables y sus exponentes) ignorando el coeficiente numérico.",
                "3. Agrupa únicamente los términos que posean factor literal idéntico.",
                "4. Opera los coeficientes numéricos respetando la ley de signos.",
                "5. Conserva intacto el factor literal común."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_ALGEBRA_SUM_EXPONENTS",
                    title = "Sumar exponentes en adición de monomios",
                    studentFaultyAssumption = "El estudiante confunde la regla de la multiplicación de potencias con la adición de términos semejantes.",
                    counterExample = "3 manzanas + 2 manzanas = 5 manzanas. Las manzanas no se convierten en 'manzanas al cuadrado'. Igualmente 3x² + 2x² = 5x², no 5x⁴.",
                    socraticRemediationPrompt = "Si tienes 5 cajas de repuestos y te quitan 8 cajas de repuestos: ¿cambia la naturaleza de la caja o solo cambia la cantidad de cajas que debes?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Identificación de Términos Semejantes",
                    socraticQuestion = "¿Tienen los términos 5x²y, -8x²y y +2x²y exactamente las mismas letras y exponentes?",
                    conceptualScaffold = "Todos tienen x²y. Por lo tanto, son semejantes y se pueden agrupar."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Operación de Coeficientes",
                    socraticQuestion = "¿Cuánto da la suma aritmética de los coeficientes: 5 - 8 + 2?",
                    conceptualScaffold = "5 - 8 = -3. Luego -3 + 2 = -1."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Ensamblaje Final",
                    socraticQuestion = "¿Cómo se escribe un coeficiente de -1 unido al factor literal x²y?",
                    conceptualScaffold = "-1x²y se escribe formalmente como -x²y."
                )
            ),
            realWorldApplication = "Cálculo de balances de energía, optimización de algoritmos, modelado de costos de producción e ingeniería de software.",
            vocationalEngineeringBridge = "Modelado paramétrico en ingeniería automotriz: cálculo de resistencia aerodinámica y curvas de par motor en función de RPM.",
            reflectionPrompt = "¿Qué sucedería si la expresión incluyera un término con 3xy²? ¿Podría sumarse con los anteriores?"
        ),

        // ── 9.º AÑO: PRODUCTOS NOTABLES (TÁRCOLES) ──────────────────────────────
        "cr_mat9_c_notables" to ConceptDeepKnowledge(
            conceptId = "cr_mat9_c_notables",
            coreIntuition = "Un producto notable es una identidad geométrica y algebraica abreviada. (a + b)² representa el área de un cuadrado de lado (a + b), que se compone de dos cuadrados (a² y b²) y dos rectángulos idénticos de área ab, resultando en a² + 2ab + b².",
            expertMentalModel = listOf(
                "1. Identifica la estructura: ¿es el cuadrado de un binomio suma (a+b)²?",
                "2. Define claramente quién es 'a' (el primer término) y quién es 'b' (el segundo término).",
                "3. Eleva el primer término al cuadrado: (a)².",
                "4. Calcula el doble producto del primero por el segundo: 2 × a × b.",
                "5. Eleva el segundo término al cuadrado: (b)².",
                "6. Ensambla el trinomio cuadrado perfecto: a² + 2ab + b²."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_NOTABLE_OMIT_MIDDLE_TERM",
                    title = "El error universal del binomio al cuadrado: (a+b)² = a² + b²",
                    studentFaultyAssumption = "El estudiante distribuye erróneamente el exponente 2 sobre la suma, omitiendo el término del medio 2ab.",
                    counterExample = "Si a=3 y b=4, entonces (3+4)² = 7² = 49. Pero 3² + 4² = 9 + 16 = 25. ¡Faltan 2 × 3 × 4 = 24 para llegar a 49!",
                    socraticRemediationPrompt = "Imagina un terreno cuadrado de (2x + 3) metros por lado. Si lo divides en parcelas: tienes un cuadrado de (2x)², otro de 3², ¿pero qué pasa con las dos esquinas rectangulares intermedias?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Identificación de Términos",
                    socraticQuestion = "En (2x + 3)², ¿cuál es el primer término 'a' y cuál es el segundo término 'b'?",
                    conceptualScaffold = "a = 2x, b = 3."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "El Doble Producto",
                    socraticQuestion = "¿Cuánto da multiplicar 2 por el primero (2x) y luego por el segundo (3)?",
                    conceptualScaffold = "2 × (2x) × 3 = 12x. Este es el término central indispensable."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Potencia del Coeficiente y la Variable",
                    socraticQuestion = "¿Cuánto da elevar (2x) completo al cuadrado?",
                    conceptualScaffold = "(2x)² = 2² × x² = 4x². Por lo tanto, el resultado es 4x² + 12x + 9."
                )
            ),
            realWorldApplication = "Cálculo de dispersión estadística (varianza), optimización cuadrática en machine learning y diseño estructural de vigas.",
            vocationalEngineeringBridge = "Cálculo de tensiones mecánicas y deformaciones en chapas de carrocería bajo impacto cinético.",
            reflectionPrompt = "¿Cómo cambiaría el desarrollo si en lugar de una suma fuera una resta: (2x - 3)²?"
        ),

        // ── 10.º/11.º AÑO: BACHILLERATO BxM (GEOMETRÍA: CIRCUNFERENCIA) ──────────
        "cr_mat_bxm_c_circunferencia" to ConceptDeepKnowledge(
            conceptId = "cr_mat_bxm_c_circunferencia",
            coreIntuition = "La circunferencia es el lugar geométrico de todos los puntos del plano que equidistan de un centro C(h,k) a una distancia fija r. Su ecuación canónica (x-h)² + (y-k)² = r² no es más que el Teorema de Pitágoras aplicado en el plano cartesiano.",
            expertMentalModel = listOf(
                "1. Escribe la forma canónica general: (x - h)² + (y - k)² = r².",
                "2. Compara con la ecuación dada prestando extrema atención a los signos dentro de los paréntesis.",
                "3. Si aparece (x - 3), entonces -h = -3 => h = 3.",
                "4. Si aparece (y + 5), entonces -k = +5 => k = -5. El centro es C(3, -5).",
                "5. Toma el valor constante a la derecha del igual: r² = 49.",
                "6. Extrae la raíz cuadrada para obtener el radio físico: r = √49 = 7."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_CIRCLE_SIGN_AND_RADIUS_SQUARE",
                    title = "Inversión de signos del centro y olvidar la raíz del radio",
                    studentFaultyAssumption = "El estudiante asume que las coordenadas del centro tienen los mismos signos que en la fórmula, y que el número al final ya es el radio directo.",
                    counterExample = "Si el centro fuera (3, 5), al sustituir en la fórmula daría (y - 5)². Para que dé (y + 5)², la coordenada original k debe haber sido negativa: (y - (-5))².",
                    socraticRemediationPrompt = "Recuerda que la fórmula base tiene signos negativos: (x - h)² + (y - k)² = r². ¿Qué valor debe tener k para que -k se transforme en +5?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Comparación con la Ecuación Canónica",
                    socraticQuestion = "¿Cuál es la ecuación canónica oficial de la circunferencia según la tabla de la DGEC?",
                    conceptualScaffold = "(x - h)² + (y - k)² = r² donde el centro es C(h,k)."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Despeje del Centro",
                    socraticQuestion = "Si (x - h) es (x - 3), ¿cuánto vale h? Si (y - k) es (y + 5), ¿cuánto vale k?",
                    conceptualScaffold = "h = 3, k = -5. Por ende C(3, -5)."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Extracción del Radio",
                    socraticQuestion = "El lado derecho es r² = 49. ¿Qué número multiplicado por sí mismo da 49?",
                    conceptualScaffold = "√49 = 7. El radio es 7 unidades."
                )
            ),
            realWorldApplication = "Geolocalización GPS (triangulación satelital), radar de tráfico aéreo en el Aeropuerto Juan Santamaría y zonas de cobertura de antenas 5G.",
            vocationalEngineeringBridge = "Mecanizado CNC y torneado automotriz: definición de tolerancias de concentricidad en cilindros de motor y discos de freno.",
            reflectionPrompt = "¿Si el radio de cobertura de una antena celular se duplica, en cuánto se multiplica el área de cobertura territorial?"
        ),

        // ── FÍSICA BXM: LEYES DE NEWTON Y FRICCIÓN ──────────────────────────────
        "cr_fis_bxm_c_newton" to ConceptDeepKnowledge(
            conceptId = "cr_fis_bxm_c_newton",
            coreIntuition = "La aceleración de un cuerpo no depende sólo de la fuerza que aplicas, sino de la fuerza NETA resultante dividida por su inercia (masa). Sin fricción en las llantas, ninguna fuerza de frenado puede desacelerar el vehículo.",
            expertMentalModel = listOf(
                "1. Aislar el sistema físico y construir el Diagrama de Cuerpo Libre (DCL).",
                "2. Identificar fuerzas actuantes: peso (P = m*g), normal (N), tracción (F) y fricción (f_r = μ*N).",
                "3. Sumar vectorialmente las fuerzas en cada eje (ΣFx = m*a_x, ΣFy = 0).",
                "4. Despejar la aceleración neta y verificar si el movimiento es acelerado o retardado.",
                "5. Relacionar la desaceleración con la distancia de detención y el coeficiente de fricción."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_FORCE_PROPORTIONAL_VELOCITY",
                    title = "Confundir fuerza neta con velocidad",
                    studentFaultyAssumption = "El estudiante asume que para moverse a gran velocidad se necesita una fuerza neta grande, o que si la fuerza cesa, el objeto se detiene instantáneamente.",
                    counterExample = "Por la 1ra Ley de Newton, en el vacío un objeto viaja a 1000 km/h indefinidamente con fuerza neta exactamente igual a CERO.",
                    socraticRemediationPrompt = "Si vas en carretera a 90 km/h y sueltas el acelerador, ¿qué fuerza específica causa que el auto se frene: la falta de acelerador o la fricción con el aire y el asfalto?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Segunda Ley de Newton",
                    socraticQuestion = "¿Cuál es la relación matemática fundamental entre fuerza neta, masa y aceleración?",
                    conceptualScaffold = "F_neta = m * a, por lo que a = F_neta / m."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Signo de la Aceleración",
                    socraticQuestion = "Si la fuerza de fricción se opone al avance del auto, ¿qué signo debe llevar la aceleración?",
                    conceptualScaffold = "Debe ser negativa (desaceleración retardatriz) respecto al vector velocidad."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Cálculo Numérico",
                    socraticQuestion = "Divide -6000 N entre 1200 kg: ¿cuánto da el cociente exacto?",
                    conceptualScaffold = "-6000 / 1200 = -5 m/s²."
                )
            ),
            realWorldApplication = "Sistemas de frenos antibloqueo (ABS), cálculo de distancia segura entre vehículos en la Ruta 27 y homologación técnica de vehículos.",
            vocationalEngineeringBridge = "Diagnóstico pericial de colisiones de tránsito y verificación del coeficiente de adherencia en el banco de frenado.",
            reflectionPrompt = "¿Por qué un tráiler cargado de 40 toneladas requiere mucha mayor distancia de frenado que un automóvil liviano a la misma velocidad?"
        ),

        // ── 7.º ESPAÑOL: TEXTO EXPOSITIVO ─────────────────────────────────────────
        "cr_esp7_c_texto_exp" to ConceptDeepKnowledge(
            conceptId = "cr_esp7_c_texto_exp",
            coreIntuition = "El texto expositivo no busca persuadir con emociones ni embellecer poéticamente, sino transmitir información objetiva, verificable y estructurada con precisión léxica.",
            expertMentalModel = listOf(
                "1. Identificar el propósito comunicativo (informar, instruir o explicar).",
                "2. Localizar la idea principal o premisa central del párrafo.",
                "3. Distinguir las ideas secundarias (ejemplos, aclaraciones, datos técnicos).",
                "4. Verificar el léxico técnico y la ausencia de subjetividades."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_FIRST_SENTENCE_ALWAYS_MAIN",
                    title = "Creer que la idea principal siempre es la primera frase",
                    studentFaultyAssumption = "El estudiante subraya siempre la primera línea sin analizar si es sólo una introducción contextual.",
                    counterExample = "Un texto puede comenzar con una anécdota y enunciar la instrucción técnica crucial al final del párrafo.",
                    socraticRemediationPrompt = "¿Si eliminas esa primera frase, el resto del texto sigue teniendo sentido completo y conserva su advertencia fundamental?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Propósito Central",
                    socraticQuestion = "¿Cuál es la acción crítica que el técnico debe realizar antes de tocar la batería?",
                    conceptualScaffold = "Fíjate en la advertencia de apagar el interruptor."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Causa y Consecuencia",
                    socraticQuestion = "¿Qué riesgo se busca evitar al apagar el switch?",
                    conceptualScaffold = "Prevenir sobretensiones que quemen la computadora del vehículo (ECU)."
                )
            ),
            realWorldApplication = "Interpretación de boletines de servicio técnico (TSB), manuales de taller automotriz y fichas técnicas de seguridad.",
            vocationalEngineeringBridge = "Redacción de bitácoras de servicio y órdenes de reparación con validez legal y de garantía comercial.",
            reflectionPrompt = "¿Cómo redactarías una instrucción de seguridad para que un aprendiz novato no cometa un error peligroso en el taller?"
        ),

        // ── 7.º CÍVICA: SEGURIDAD VIAL (LEY 9078) ─────────────────────────────────
        "cr_civ7_c_seguridad_vial" to ConceptDeepKnowledge(
            conceptId = "cr_civ7_c_seguridad_vial",
            coreIntuition = "La vía pública es un bien común. Conducir o circular por ella exige un pacto social ético y legal: la libertad de tránsito termina donde comienza el derecho a la vida y la integridad física de los demás.",
            expertMentalModel = listOf(
                "1. Reconocer la trilogía vial: factor humano, factor vehicular y factor ambiental.",
                "2. Identificar las normas de tránsito como mecanismos de protección colectiva.",
                "3. Analizar cómo el estado mecánico del vehículo impacta directamente en el riesgo vial.",
                "4. Asumir la cultura del autocuidado y la prevención activa de siniestros."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_VIAL_PUNITIVE_ONLY",
                    title = "Visión punitiva en lugar de preventiva",
                    studentFaultyAssumption = "Pensar que revisar el auto o respetar señales sólo sirve para que el oficial de tránsito no aplique una boleta económica.",
                    counterExample = "Una llanta lisa a 80 km/h bajo un aguacero en el Zurquí provoca hidroplaneo fatal, exista o no un policía presente.",
                    socraticRemediationPrompt = "¿El peligro de muerte depende de la presencia de un inspector o de las leyes físicas de fricción sobre el asfalto mojado?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Deber Cívico",
                    socraticQuestion = "¿Por qué la Ley de Tránsito 9078 tipifica el buen estado del vehículo como una obligación legal?",
                    conceptualScaffold = "Porque un fallo mecánico en carretera afecta a peatones y a otros conductores inocentes."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Protección de la Vida",
                    socraticQuestion = "¿Cuál es el bien jurídico supremo tutelado en la legislación de seguridad vial costarricense?",
                    conceptualScaffold = "El derecho constitucional a la vida y la salud pública."
                )
            ),
            realWorldApplication = "Inspección técnica vehicular (DEKRA/Cosevi), educación vial comunitaria y prevención de accidentes en rutas nacionales.",
            vocationalEngineeringBridge = "Certificación de inspección previa a la entrega de un vehículo en talleres mecánicos profesionales.",
            reflectionPrompt = "¿Qué pasaría en una ciudad donde cada conductor decidiera libremente a qué velocidad viajar y si enciende o no las luces de noche?"
        )
    )

    fun getKnowledgeForConcept(concept: CurriculumConceptData): ConceptDeepKnowledge {
        val registered = REGISTRY[concept.id]
        if (registered != null) return registered

        // Procedural Socratic Synthesizer for concepts not yet explicitly registered in the map
        return ConceptDeepKnowledge(
            conceptId = concept.id,
            coreIntuition = "El concepto '${concept.title}' establece los fundamentos de comprensión y aplicación en ${concept.conceptCode}. Su dominio permite razonar sobre problemas del mundo real y conectar con habilidades de orden superior.",
            expertMentalModel = listOf(
                "1. Leer analíticamente los datos y condiciones del problema.",
                "2. Identificar el principio conceptual rector (${concept.title}).",
                "3. Seleccionar la estrategia o fórmula canónica correspondiente.",
                "4. Ejecutar el cálculo o razonamiento verificando la coherencia de unidades y signos.",
                "5. Validar que la respuesta sea físicamente y lógicamente plausible."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_GENERIC_SUPERFICIAL_HEURISTIC",
                    title = "Aplicación mecánica sin comprensión de fondo",
                    studentFaultyAssumption = "Elegir opciones por patrones superficiales o palabras familiares sin verificar el enunciado.",
                    counterExample = "En pruebas oficiales del MEP, los distractores se diseñan precisamente reproduciendo los errores de cálculo más comunes.",
                    socraticRemediationPrompt = "¿Por qué descartaste las otras opciones? ¿Qué regla matemática o científica sustenta tu elección?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Orientación Inicial",
                    socraticQuestion = "¿Qué es exactamente lo que te solicita responder el reactivo?",
                    conceptualScaffold = "Identifica la incógnita principal y descarta distractores evidentes."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Principio Conceptual",
                    socraticQuestion = "¿Cuál regla o propiedad oficial del MEP aplica directamente en este caso?",
                    conceptualScaffold = concept.description
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Conexión Práctica",
                    socraticQuestion = "¿Cómo aplicarías este razonamiento si estuvieras resolviendo una situación real en un taller o proyecto?",
                    conceptualScaffold = "Piensa en el impacto físico del resultado numérico o lógico."
                )
            ),
            realWorldApplication = "Aplicación directa en toma de decisiones informadas, pensamiento crítico y certificación nacional MEP/DGEC.",
            vocationalEngineeringBridge = "Base formativa para la inserción técnica y laboral en el mercado productivo nacional.",
            reflectionPrompt = "¿Cómo le explicarías este principio a otra persona que está aprendiendo por primera vez?"
        )
    }
}
