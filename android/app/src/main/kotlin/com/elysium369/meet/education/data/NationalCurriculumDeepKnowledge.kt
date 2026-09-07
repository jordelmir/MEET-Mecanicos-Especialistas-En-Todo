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
        ),

        // ── QUÍMICA BXM: ESTEQUIOMETRÍA Y REACCIONES ─────────────────────────────
        "cr_quim_bxm_c_estequiometria" to ConceptDeepKnowledge(
            conceptId = "cr_quim_bxm_c_estequiometria",
            coreIntuition = "La masa no se crea ni se destruye; se reorganiza a nivel atómico. El mol es el puente matemático que conecta el microcosmos molecular con la balanza macroscópica de laboratorio y los sistemas de inyección.",
            expertMentalModel = listOf(
                "1. Escribir la ecuación química completa y balancearla rigurosamente asegurando la conservación de masa.",
                "2. Convertir las masas conocidas en gramos a moles dividiendo entre la masa molar (g/mol).",
                "3. Comparar las razones molares de la ecuación para identificar el reactivo limitante que agotará la reacción.",
                "4. Calcular los moles teóricos de producto esperados a partir del reactivo limitante.",
                "5. Convertir los moles de producto a gramos y aplicar el porcentaje de rendimiento experimental si aplica."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_MASS_CONSERVATION_COEFFICIENTS",
                    title = "Confusión entre coeficientes estequiométricos y gramos",
                    studentFaultyAssumption = "El estudiante asume que los coeficientes de una ecuación química representan masas directamente en gramos en vez de moles de moléculas.",
                    counterExample = "En 2H2 + O2 -> 2H2O, 4 g de hidrógeno reaccionan con 32 g de oxígeno; los números 2 y 1 indican moles, no proporciones directas de peso.",
                    socraticRemediationPrompt = "¿Los coeficientes balanceados indican cuántos gramos pesan las sustancias o cuántos paquetes de moléculas (moles) están interactuando?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Balanceo Inicial",
                    socraticQuestion = "¿La ecuación química tiene el mismo número de átomos de cada elemento a la izquierda y a la derecha?",
                    conceptualScaffold = "Verifica primero el balance de materia antes de realizar cualquier cálculo numérico."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Conversión a Moles",
                    socraticQuestion = "¿Cuántos moles representa la masa de reactivo que te proporciona el problema?",
                    conceptualScaffold = "Divide los gramos dados entre el peso molecular de la sustancia: n = m / PM."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Analogía de Taller",
                    socraticQuestion = "¿Cómo se calcula la relación estequiométrica aire-combustible (14.7:1) en la combustión de gasolina?",
                    conceptualScaffold = "1 mol de octano requiere exactamente 12.5 moles de O2 para combustión completa; una mezcla rica genera hidrocarburos sin quemar."
                )
            ),
            realWorldApplication = "Dosificación de aditivos industriales, control de emisiones de escape y cálculo estequiométrico en combustión interna.",
            vocationalEngineeringBridge = "ISCO 7231: Diagnóstico de sistemas de inyección electrónica, sensores Lambda y lectura de corrección de combustible (Fuel Trim).",
            reflectionPrompt = "¿Qué consecuencias físicas y ambientales tiene en el catalizador un motor que trabaja constantemente con mezcla rica en combustible?"
        ),

        // ── BIOLOGÍA BXM: GENÉTICA Y CUADROS DE PUNNETT ─────────────────────────
        "cr_bio_bxm_c_genetica" to ConceptDeepKnowledge(
            conceptId = "cr_bio_bxm_c_genetica",
            coreIntuition = "La información biológica se codifica en secuencias de nucleótidos que se transmiten según probabilidades combinatorias discretas. Los alelos dominantes enmascaran a los recesivos en el fenotipo, pero no los alteran en el genotipo.",
            expertMentalModel = listOf(
                "1. Identificar el genotipo de ambos progenitores (homocigoto dominante AA, heterocigoto Aa, o recesivo aa).",
                "2. Determinar los alelos que cada progenitor puede aportar a través de sus gametos.",
                "3. Construir el cuadro de Punnett colocando los gametos parentales en los ejes vertical y horizontal.",
                "4. Completar las combinaciones genotípicas de la descendencia en las celdas interiores.",
                "5. Calcular las proporciones genotípicas y fenotípicas porcentuales esperadas."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_DOMINANT_EQUALS_COMMON",
                    title = "Confusión de dominancia genética con frecuencia poblacional",
                    studentFaultyAssumption = "Creer que un alelo dominante es siempre el más abundante o ventajoso en una población silvestre.",
                    counterExample = "La polidactilia (dedos extra) es causada por un alelo dominante, pero la inmensa mayoría de la población posee cinco dedos por ser homocigota recesiva.",
                    socraticRemediationPrompt = "¿Que un alelo enmascare a otro cuando ambos están presentes significa necesariamente que sea más común en la naturaleza?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Identificación de Alelos",
                    socraticQuestion = "¿Cuáles son los alelos que porta cada progenitor para el rasgo estudiado?",
                    conceptualScaffold = "Usa mayúsculas para alelos dominantes (A) y minúsculas para recesivos (a)."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Cuadro de Punnett",
                    socraticQuestion = "¿Cuáles combinaciones resultan al cruzar dos individuos heterocigotos (Aa x Aa)?",
                    conceptualScaffold = "Obtendrás 1 AA : 2 Aa : 1 aa (relación fenotípica 3:1 dominante a recesivo)."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Analogía Digital",
                    socraticQuestion = "¿Cómo se asemeja el código genético a una palabra binaria con bits de control de paridad?",
                    conceptualScaffold = "Cada triplete de nucleótidos (codón) codifica un aminoácido exacto con redundancia para proteger contra mutaciones letales."
                )
            ),
            realWorldApplication = "Medicina forense, pruebas de paternidad con marcadores STR y mejoramiento agronómico de semillas en Costa Rica.",
            vocationalEngineeringBridge = "ISCO 2131: Biólogos, genetistas y técnicos de laboratorio de control biológico.",
            reflectionPrompt = "¿Por qué dos personas con fenotipo dominante pueden tener un hijo con fenotipo recesivo sin contradecir las leyes de Mendel?"
        ),

        // ── ESTUDIOS SOCIALES BXM: COSTA RICA EN EL SIGLO XX ─────────────────────
        "cr_soc_bxm_c_cr_siglo_xx" to ConceptDeepKnowledge(
            conceptId = "cr_soc_bxm_c_cr_siglo_xx",
            coreIntuition = "El modelo social costarricense contemporáneo no fue un accidente fortuito; fue el fruto de un pacto sociopolítico visionario en 1940 y la refundación constitucional de 1949 que abolió el ejército para blindar la inversión en educación y salud.",
            expertMentalModel = listOf(
                "1. Analizar el contexto de vulnerabilidad del modelo agroexportador cafetalero y bananero ante la crisis mundial.",
                "2. Comprender la alianza tripartita de 1940: Dr. Rafael Ángel Calderón Guardia, Monseñor Sanabria y Manuel Mora.",
                "3. Evaluar las tres grandes instituciones creadas: CCSS (1941), Universidad de Costa Rica (1940) y Código de Trabajo (1943).",
                "4. Analizar la Guerra Civil de 1948 y las decisiones de la Junta Fundadora de José Figueres Ferrer.",
                "5. Sintetizar los pilares de la Constitución de 1949: abolición del ejército, creación del TSE y sufragio universal femenino."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_ARMY_ABOLISHED_1821",
                    title = "Confusión cronológica en la abolición del ejército",
                    studentFaultyAssumption = "El estudiante asume que Costa Rica nunca tuvo fuerzas armadas o que el ejército se abolió en la independencia colonial.",
                    counterExample = "Costa Rica tuvo ejército durante todo el siglo XIX y combatió con honor en la Campaña Nacional de 1856; el ejército fue abolido el 1 de diciembre de 1948.",
                    socraticRemediationPrompt = "¿En qué año y quién dio el histórico mazazo en el Cuartel Bellavista que transformó el cuartel militar en el Museo Nacional?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Las Garantías Sociales",
                    socraticQuestion = "¿Cuáles tres grandes reformas sociales se aprobaron en la administración del Dr. Calderón Guardia?",
                    conceptualScaffold = "Recuerda la CCSS, el Código de Trabajo y la refundación de la Universidad de Costa Rica."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Pacto Constitucional",
                    socraticQuestion = "¿Qué impacto tuvo incluir las Garantías Sociales en el texto de la Constitución Política de 1949?",
                    conceptualScaffold = "Se convirtieron en derechos fundamentales de rango constitucional que ningún gobierno ordinario puede suprimir."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Impacto Económico Real",
                    socraticQuestion = "¿Cómo influye la ausencia de gasto militar en la disponibilidad de fondos para la red hospitalaria y escuelas públicas?",
                    conceptualScaffold = "El gasto que otros países destinan a defensa en Costa Rica se reconvirtió en capital humano productivo."
                )
            ),
            realWorldApplication = "Participación ciudadana informada, valoración del seguro social universal y preservación de la estabilidad institucional.",
            vocationalEngineeringBridge = "ISCO 3353: Especialistas en gestión de la seguridad social, normativa laboral y auditoría del Estado.",
            reflectionPrompt = "¿Qué ventajas competitivas ofrece a las empresas internacionales instalarse en un país con paz social y sin presupuesto bélico?"
        ),

        // ── EDUCACIÓN CÍVICA BXM: RÉGIMEN DEMOCRÁTICO Y DEFENSA CONSTITUCIONAL ───
        "cr_civ_bxm_c_democracia" to ConceptDeepKnowledge(
            conceptId = "cr_civ_bxm_c_democracia",
            coreIntuition = "La democracia plena exige frenos, contrapesos e instrumentos jurídicos de acceso universal. La Sala Constitucional permite a cualquier habitante exigir la tutela de sus derechos fundamentales sin formalismos ni intermediación obligatoria de abogados.",
            expertMentalModel = listOf(
                "1. Comprender la división tripartita clásica de poderes y el rango autónomo constitucional del TSE.",
                "2. Identificar la naturaleza del derecho vulnerado (libertad ambulatoria vs. otros derechos fundamentales).",
                "3. Distinguir la vía procesal idónea: Hábeas Corpus para la libertad física; Recurso de Amparo para salud, educación, trabajo y ambiente.",
                "4. Redactar el memorial con hechos claros, prueba testimonial o documental y petición concreta.",
                "5. Reconocer el carácter vinculante erga omnes de las resoluciones de la Sala Constitucional."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_AMPARO_LAWYER_REQUIRED",
                    title = "Falso requisito de patrocinio letrado en la Sala IV",
                    studentFaultyAssumption = "Creer que interponer un recurso de amparo requiere contratar a un abogado y pagar timbres judiciales costosos.",
                    counterExample = "Cualquier ciudadano, incluso un menor de edad o persona extranjera, puede presentar un amparo en una servilleta escrita a mano sin abogado.",
                    socraticRemediationPrompt = "¿Por qué la Ley de la Jurisdicción Constitucional eliminó los requisitos formales y el costo para interponer un amparo?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Derecho en Peligro",
                    socraticQuestion = "¿Qué derecho fundamental está siendo amenazado o desconocido por el acto de la autoridad?",
                    conceptualScaffold = "Identifica si es la salud, la educación, el debido proceso o la libertad física."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Vía Procesal Correcta",
                    socraticQuestion = "Si a un paciente la CCSS le retrasa una cirugía urgente, ¿cuál recurso debe interponer ante la Sala IV?",
                    conceptualScaffold = "Corresponde un Recurso de Amparo para tutelar el derecho fundamental a la vida y la salud."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Parada de Emergencia",
                    socraticQuestion = "¿Por qué la interposición de un amparo suspende de inmediato los efectos del acto lesivo?",
                    conceptualScaffold = "Funciona como el botón de paro de emergencia de un taller: congela el daño mientras se analiza la legalidad."
                )
            ),
            realWorldApplication = "Defensa efectiva de los derechos de usuarios de servicios públicos, cumplimiento de la Ley 7600 y protección ambiental.",
            vocationalEngineeringBridge = "ISCO 2422: Asesores en cumplimiento legal, derechos laborales y relaciones de servicio en talleres e industrias.",
            reflectionPrompt = "¿Cómo protege la existencia de la Sala Constitucional a las minorías frente a decisiones arbitrarias de las mayorías parlamentarias?"
        ),

        // ── INGLÉS BXM: TECHNICAL READING & PASSIVE VOICE ────────────────────────
        "cr_ing_bxm_c_reading" to ConceptDeepKnowledge(
            conceptId = "cr_ing_bxm_c_reading",
            coreIntuition = "In international technical and STEM documentation, passive voice ensures clinical objectivity. By putting the physical system or component before the human operator, engineering instructions highlight the procedure over the person.",
            expertMentalModel = listOf(
                "1. Identify passive voice structure: Target Object + Form of 'to be' + Past Participle.",
                "2. Scan the technical document for quantitative values, tolerances, torque specs, and diagnostic codes.",
                "3. Use root words, prefixes, and suffixes to deduce technical terminology without word-for-word translation.",
                "4. Identify causal and sequential transition words (subsequently, therefore, provided that).",
                "5. Translate technical instructions into verifiable workshop verification actions."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_PASSIVE_CONFUSION",
                    title = "Confusión entre el sujeto gramatical y el ejecutor de la acción",
                    studentFaultyAssumption = "Asumir que el primer sustantivo de la frase pasiva es quien realiza el trabajo físico.",
                    counterExample = "In 'The brake rotors were replaced', the rotors did not replace anything; they received the replacement action.",
                    socraticRemediationPrompt = "Who or what is performing the action in this sentence? Is the alternator testing the technician or being tested?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Target Identification",
                    socraticQuestion = "What component receives the action described in the sentence?",
                    conceptualScaffold = "Look for the noun immediately before the auxiliary verb 'was/were/is/are'."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Grammar Pattern",
                    socraticQuestion = "Which verb tense follows the auxiliary 'must be' or 'has been'?",
                    conceptualScaffold = "Passive voice always uses the past participle form of the main action verb."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "OEM Workshop Manuals",
                    socraticQuestion = "How does 'The cylinder head bolts must be torqued to 85 Nm' guide your mechanical tool setting?",
                    conceptualScaffold = "Set the torque wrench to 85 Nm before tightening the bolts in the specified star pattern."
                )
            ),
            realWorldApplication = "Reading OEM Factory Service Manuals, international technical service bulletins (TSBs), and vehicle diagnostic scanner outputs.",
            vocationalEngineeringBridge = "ISCO 3512 / 7231: Bilingual automotive technician and technical support specialist for diagnostic telemetry.",
            reflectionPrompt = "Why do international engineering service manuals avoid personal pronouns like 'I' and 'we'?"
        ),

        // ── INGLÉS C1: ADVANCED SYNTACTIC MASTERY (INVERSION & CLEFTS) ───────────
        "cr_ing_c1_c_inversion_cleft" to ConceptDeepKnowledge(
            conceptId = "cr_ing_c1_c_inversion_cleft",
            coreIntuition = "In high-stakes technical, safety and legal documentation, standard word order is inverted after negative adverbials (under no circumstances, seldom, not only) to signal absolute mandates, while cleft sentences pinpoint root causes.",
            expertMentalModel = listOf(
                "1. Recognize the initial restrictive or negative adverbial trigger (under no circumstances, not only, seldom).",
                "2. Position the auxiliary verb immediately before the grammatical subject (auxiliary + subject + main verb).",
                "3. In cleft structures, place the focal root-cause element into the cleft clause ('It was X that...' or 'What X did was...').",
                "4. Verify that no double negatives or incorrect auxiliary inflections are introduced.",
                "5. Ensure the sentence delivers unambiguous, authoritative professional emphasis."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_INVERSION_OMISSION",
                    title = "Omisión de inversión auxiliar-sujeto tras adverbiales negativos",
                    studentFaultyAssumption = "Mantener el orden sintáctico afirmativo (sujeto + verbo) al iniciar con frases como 'Under no circumstances' o 'Seldom'.",
                    counterExample = "Writing 'Under no circumstances the technician should probe' violates formal C1 syntax; it must be 'Under no circumstances should the technician probe'.",
                    socraticRemediationPrompt = "When an English sentence begins with a restrictive negative adverbial, what happens to the auxiliary verb? Does it stay after the subject or move before it?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Adverbial Trigger",
                    socraticQuestion = "When a sentence starts with 'Under no circumstances', does the subject come before or after the modal verb 'should'?",
                    conceptualScaffold = "Invert the subject and auxiliary verb: 'should the technician probe'."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Question Order Pattern",
                    socraticQuestion = "Notice how negative inversion mirrors formal question word order: 'Should you do it?' -> 'Under no circumstances should you do it.'",
                    conceptualScaffold = "Use standard question syntax after negative introductory adverbials."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "High-Voltage Safety",
                    socraticQuestion = "How does inverted syntax communicate non-negotiable safety in an EV high-voltage service manual?",
                    conceptualScaffold = "It creates immediate grammatical prominence, ensuring critical safety rules cannot be misunderstood."
                )
            ),
            realWorldApplication = "Writing safety-critical operational procedures, forensic accident affidavits, and international patent claims.",
            vocationalEngineeringBridge = "ISCO 2144 / 7231: Automotive and mechanical engineers drafting technical compliance documents and OEM bulletins.",
            reflectionPrompt = "How does inverted syntax alter the perceived authority and legal liability of a safety warning compared to a standard declarative sentence?"
        ),

        // ── INGLÉS C1: MIXED CONDITIONALS & FORENSIC CAUSALITY ───────────────────
        "cr_ing_c1_c_mixed_conditionals" to ConceptDeepKnowledge(
            conceptId = "cr_ing_c1_c_mixed_conditionals",
            coreIntuition = "Mixed conditionals bridge asynchronous temporal frames. They connect a counterfactual past decision (Past Perfect) with an ongoing present reality (would + infinitive), or an inherent permanent condition with a past breakdown.",
            expertMentalModel = listOf(
                "1. Determine whether the hypothesis is in the past (Past Perfect) or represents an enduring characteristic (Past Simple).",
                "2. Determine whether the consequence is an ongoing present condition (would + infinitive) or a past event (would have + participle).",
                "3. Formulate the mixed conditional pair: Past condition + Present result (Type 3 + Type 2).",
                "4. In formal registers, apply conditional inversion omitting 'if' ('Had the technician torqued...').",
                "5. Verify logical cause-and-effect consistency in root-cause fault tracing."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_RIGID_CONDITIONALS",
                    title = "Creer que las oraciones condicionales no pueden mezclar tiempos",
                    studentFaultyAssumption = "Asumir que las condicionales solo pueden ser Tipo 1, 2 o 3 rígidas sin combinar pasado y presente.",
                    counterExample = "'If I had won the lottery yesterday, I would be rich today' perfectly combines a past event with a present state of being.",
                    socraticRemediationPrompt = "Can an action that failed yesterday produce a consequence that is still happening right now? Which tenses express that relationship?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Timeframes Separation",
                    socraticQuestion = "Did the bolt tightening happen yesterday or right now? Is the engine leaking yesterday or right now?",
                    conceptualScaffold = "Combine the past action (had torqued) with the present consequence (would not be leaking)."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Conditional Inversion",
                    socraticQuestion = "How do you rewrite 'If the mechanic had verified the clearance' in a formal C1 register without 'if'?",
                    conceptualScaffold = "Omit 'if' and invert: 'Had the mechanic verified the clearance...'"
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Forensic Root-Cause",
                    socraticQuestion = "How does mixed conditional phrasing establish causal liability in a vehicle warranty claim dispute?",
                    conceptualScaffold = "It demonstrates beyond reasonable doubt that a specific maintenance omission directly produced the ongoing physical defect."
                )
            ),
            realWorldApplication = "Root-cause failure analysis (RCFA), insurance forensic investigations, and warranty arbitration proceedings.",
            vocationalEngineeringBridge = "ISCO 7231 / 3115: Lead diagnostic technicians, forensic assessors, and engineering warranty auditors.",
            reflectionPrompt = "Why is counterfactual conditional analysis essential when defending a warranty denial before a judicial arbiter?"
        ),

        // ── INGLÉS C1: PROFESSIONAL STEM TELEMETRY & HEDGING ─────────────────────
        "cr_ing_c1_c_telemetry_hedging" to ConceptDeepKnowledge(
            conceptId = "cr_ing_c1_c_telemetry_hedging",
            coreIntuition = "C1 professional discourse avoids categorical overstatements. Objective engineers use modal hedging to separate empirically measured physical data from causal hypotheses, protecting their credibility and legal defensibility.",
            expertMentalModel = listOf(
                "1. Present verified sensor readings and telemetry data in exact quantitative terms.",
                "2. Transition from raw data to causal deduction using cautious epistemic verbs (suggest, indicate, appear to).",
                "3. Qualify the extent of certainty using modal auxiliaries (may have, might indicate, would suggest).",
                "4. Eliminate emotive, subjective, or accusatory language from the formal technical brief.",
                "5. Conclude with verifiable, actionable remediation recommendations."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_BLUNT_CATEGORICAL_ASSERTION",
                    title = "Uso de afirmaciones absolutas no demostradas en reportes periciales",
                    studentFaultyAssumption = "Creer que sonar autoritario requiere hacer afirmaciones categóricas como 'el conductor definitivamente destruyó el motor'.",
                    counterExample = "In judicial and insurance cross-examinations, unhedged absolute statements are easily overturned by defense counsel; hedged assertions remain bulletproof.",
                    socraticRemediationPrompt = "Is there room for another plausible physical cause based solely on this single sensor reading? How can you express deduction without declaring unverified certainty?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Distinguishing Data from Claims",
                    socraticQuestion = "Is a sensor reading of 4.8V a physical fact or an opinion? Is the claim that the sensor is damaged a fact or a deduction?",
                    conceptualScaffold = "The voltage is raw data; the diagnosis is an inference that requires hedged phrasing."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Modal Hedging Tools",
                    socraticQuestion = "Which modal phrases soften an assertion into a scientifically defensible deduction?",
                    conceptualScaffold = "Use 'The evidence suggests that...', 'This would appear to indicate...', 'may have contributed to...'."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Affidavit Admissibility",
                    socraticQuestion = "Why do international arbitration panels prefer hedged engineering reports over emotionally charged accusations?",
                    conceptualScaffold = "Because hedged reports rely strictly on reproducible empirical data, maintaining objective scientific integrity."
                )
            ),
            realWorldApplication = "Authoring forensic mechanical affidavits, engineering change proposals, and international arbitration submissions.",
            vocationalEngineeringBridge = "ISCO 2422 / 3115: Technical inspectors, expert witnesses, and international engineering consultants.",
            reflectionPrompt = "How does modal hedging enhance, rather than weaken, the scientific credibility and legal resilience of a diagnostic report?"
        ),

        // ── 8.º AÑO: DIBUJO TÉCNICO CAD Y PROYECCIONES ORTOGONALES ──────────────
        "cr_art_c_dibujo_tecnico" to ConceptDeepKnowledge(
            conceptId = "cr_art_c_dibujo_tecnico",
            coreIntuition = "El dibujo técnico es el lenguaje geométrico universal de la ingeniería. Permite proyectar objetos tridimensionales en planos bidimensionales normalizados sin distorsión óptica, garantizando que una pieza diseñada en Costa Rica se manufacture idéntica en cualquier torno o fresadora del mundo.",
            expertMentalModel = listOf(
                "1. Seleccionar la vista frontal (alzado) que revele la mayor cantidad de rasgos característicos de la pieza.",
                "2. Proyectar rayos paralelos a 90 grados sobre los planos principales de proyección.",
                "3. Disponer la vista superior (planta) y la lateral manteniendo estricta correspondencia dimensional.",
                "4. Representar aristas ocultas con líneas de trazos cortos continuos y ejes con línea y punto.",
                "5. Acotar dimensiones siguiendo las normas ISO/ANSI sin redundancias ni cruces de líneas de cota."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_PERSPECTIVE_VS_ORTHOGONAL",
                    title = "Confusión entre perspectiva artística y proyección ortogonal",
                    studentFaultyAssumption = "Dibujar las vistas proyectando líneas convergentes a un punto de fuga en lugar de rayos paralelos a 90 grados.",
                    counterExample = "Si un plano técnico convergiera a un punto de fuga, el extremo posterior de un pistón mediría menos en el plano que el frontal.",
                    socraticRemediationPrompt = "¿En una proyección ortogonal las líneas de proyección son paralelas entre sí o se juntan en un punto de fuga?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Selección de Vista",
                    socraticQuestion = "¿Cuál vista del objeto muestra la mayor cantidad de información y contornos?",
                    conceptualScaffold = "Esa vista debe elegirse como la vista frontal o principal."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Correspondencia de Aristas",
                    socraticQuestion = "Si trazas una línea vertical desde el extremo derecho de la vista frontal hacia arriba, ¿con qué debe coincidir?",
                    conceptualScaffold = "Debe coincidir exactamente con el límite derecho de la vista superior."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Mecanizado en Taller",
                    socraticQuestion = "¿Qué sucedería si omites las líneas de trazo discontinuo que representan un canal interno en una pieza?",
                    conceptualScaffold = "El tornero o fresador no sabrá que debe perforar el interior y la pieza quedará sólida e inservible."
                )
            ),
            realWorldApplication = "Diseño de partes mecánicas, planos estructurales, matricería y corte por plasma/láser CNC.",
            vocationalEngineeringBridge = "ISCO 3118: Delineantes técnicos y diseñadores CAD/CAM mecánicos e industriales.",
            reflectionPrompt = "¿Por qué es una falta grave en dibujo técnico acotar la misma medida dos veces en vistas diferentes?"
        ),

        // ── 9.º AÑO: METROLOGÍA Y ELECTRICIDAD RESIDENCIAL / TALLER ─────────────
        "cr_art_c_electricidad" to ConceptDeepKnowledge(
            conceptId = "cr_art_c_electricidad",
            coreIntuition = "La energía eléctrica en circuitos cerrados obedece a leyes físicas deterministas. La tensión empuja la corriente a través de una resistencia; cualquier resistencia parásita por corrosión o falso contacto provoca caída de tensión y calor indeseado según la Ley de Joule.",
            expertMentalModel = listOf(
                "1. Identificar la fuente de tensión y verificar la polaridad o tipo de corriente (DC en baterías, AC en red pública).",
                "2. Rastrear el circuito cerrado: línea activa (fase o positivo), carga de trabajo y retorno (neutro o tierra a chasis).",
                "3. Verificar la capacidad de los elementos de protección (fusibles o disyuntores termomagnéticos).",
                "4. Medir caídas de tensión a lo largo de conductores y contactos usando un multímetro digital en voltios.",
                "5. Comprobar la continuidad de tierra física para garantizar la disipación segura de corrientes de falla."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_VOLTAGE_FLOWS_THROUGH",
                    title = "Creer que 'el voltaje fluye' por el cable",
                    studentFaultyAssumption = "Confundir la tensión eléctrica (diferencia de potencial) con la corriente eléctrica (flujo de electrones).",
                    counterExample = "Un tomacorriente tiene 120 voltios presentes constantemente aunque no haya ningún aparato conectado y la corriente sea cero amperios.",
                    socraticRemediationPrompt = "¿El voltaje es la velocidad a la que viajan los electrones o la fuerza/presión con la que la fuente los empuja?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Lazo Cerrado",
                    socraticQuestion = "¿El circuito eléctrico tiene un camino continuo de ida y vuelta a la fuente de poder?",
                    conceptualScaffold = "Si hay un cable roto o un interruptor abierto, la corriente es exactamente cero."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Ley de Ohm",
                    socraticQuestion = "Si la resistencia de un contacto sulfatado sube a 10 ohmios en un circuito de 12V, ¿cuánta corriente puede pasar?",
                    conceptualScaffold = "Aplica I = V / R. Al aumentar la resistencia, la corriente útil se reduce drásticamente."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Caída de Tensión Automotriz",
                    socraticQuestion = "¿Cómo se utiliza la prueba de caída de tensión para detectar un cable de masa oxidado sin desmontarlo?",
                    conceptualScaffold = "Coloca las puntas del multímetro entre el borne negativo de la batería y el bloque del motor mientras das arranque: debe marcar menos de 0.2V."
                )
            ),
            realWorldApplication = "Instalaciones eléctricas residenciales según Código Eléctrico de Costa Rica (RTCR/NEC), diagnóstico de arneses y tableros de control.",
            vocationalEngineeringBridge = "ISCO 7412: Mecánicos y ajustadores electricistas residenciales y automotrices.",
            reflectionPrompt = "¿Por qué un disyuntor termomagnético protege al cableado de un incendio pero no necesariamente protege a una persona de una descarga letal (requiriéndose un GFCI)?"
        ),

        // ── CIENCIAS 7.º/8.º AÑO: LA CÉLULA Y LA VIDA ────────────────────────────
        "cr_cien_c_celula" to ConceptDeepKnowledge(
            conceptId = "cr_cien_c_celula",
            coreIntuition = "La célula es la unidad morfológica, funcional y genética más pequeña dotada de vida independiente. En su interior, una maquinaria molecular altamente coordinada procesa nutrientes, replica información genética y genera energía química en forma de ATP.",
            expertMentalModel = listOf(
                "1. Diferenciar si la célula posee núcleo delimitado por membrana (eucariota) o no (procariota).",
                "2. Reconocer las organelas distintivas: mitocondrias (energía), cloroplastos (fotosíntesis vegetal), ribosomas (proteínas).",
                "3. Comprender la función de la membrana plasmática como barrera semipermeable selectiva.",
                "4. Rastrear la respiración celular: glucosa + oxígeno -> dióxido de carbono + agua + ATP.",
                "5. Conectar la fisiología celular con el impacto de toxinas ambientales y gases de escape en la respiración de los tejidos."
            ),
            misconceptions = listOf(
                CognitiveMisconceptionDetail(
                    code = "MISCONCEPTION_ANIMAL_CELL_WALL",
                    title = "Atribuir pared celular a las células de origen animal",
                    studentFaultyAssumption = "Creer que las células animales tienen pared de celulosa como las vegetales.",
                    counterExample = "Si las células animales tuvieran pared rígida de celulosa, los músculos no podrían contraerse ni los seres vivos moverse con flexibilidad.",
                    socraticRemediationPrompt = "¿Qué estructura permite a las plantas mantenerse erguidas sin huesos, y por qué las células de tu piel son flexibles?"
                )
            ),
            socraticHintTiers = listOf(
                SocraticHintTier(
                    tierLevel = 1,
                    title = "Centro de Control",
                    socraticQuestion = "¿En qué organela se almacena el material genético (ADN) en las células eucariotas?",
                    conceptualScaffold = "El núcleo celular alberga las instrucciones maestras para la síntesis de proteínas."
                ),
                SocraticHintTier(
                    tierLevel = 2,
                    title = "Central de Energía",
                    socraticQuestion = "¿Cuál organela realiza la combustión biológica de nutrientes mediante respiración celular?",
                    conceptualScaffold = "Las mitocondrias producen el ATP necesario para todas las funciones vitales."
                ),
                SocraticHintTier(
                    tierLevel = 3,
                    title = "Toxicología en Taller",
                    socraticQuestion = "¿Por qué el monóxido de carbono (CO) de los motores de gasolina es letal a nivel celular?",
                    conceptualScaffold = "El CO se une a la hemoglobina 200 veces más fuerte que el oxígeno, impidiendo que las mitocondrias celulares reciban oxígeno para producir ATP."
                )
            ),
            realWorldApplication = "Biología ambiental, toxicología ocupacional y protocolos de bioseguridad en ambientes de trabajo.",
            vocationalEngineeringBridge = "ISCO 3257: Inspectores de salud ambiental y seguridad ocupacional en industrias y talleres.",
            reflectionPrompt = "¿Qué ventajas evolutivas tienen las células eucariotas compartimentadas frente a las bacterias procariotas primitivas?"
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
