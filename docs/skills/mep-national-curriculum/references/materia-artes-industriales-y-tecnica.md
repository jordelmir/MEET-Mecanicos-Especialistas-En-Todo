# Desarrollo Pedagógico y Técnico: Artes Industriales y Metrología MEP (7.º, 8.º y 9.º)

Este documento contiene la especificación curricular oficial, práctica e industrial del área de Artes Industriales y Educación Técnica del Tercer Ciclo de la Educación General Básica (EGB) del Ministerio de Educación Pública (MEP) de Costa Rica.

---

## 1. Fundamentación Curricular y Enfoque Práctico-Vocacional

- **Enfoque Pedagógico**: Educación Técnica Vocacional ("Aprender Haciendo" / Hands-on Learning).
- **Pilares Formativos**:
  1. *Seguridad Industrial y Salud Ocupacional*: Identificación de peligros, evaluación de riesgos y uso de Equipos de Protección Personal (EPP: lentes de policarbonato, guantes de nitrilo/cuero, calzado con puntera de protección y protección auditiva).
  2. *Metrología de Precisión*: Medición dimensional exacta, interpretación de tolerancias geométricas y eliminación del error humano en taller.
  3. *Interpretación y Elaboración Gráfica (CAD)*: Lenguaje universal del plano técnico como base para la manufactura y la construcción.
  4. *Instalaciones y Mantenimiento Físico*: Técnicas rigurosas de fontanería, electricidad básica y mecánica de fluidos aplicadas.

---

## 2. Desarrollo Curricular por Grados

### 7.º Año: Fontanería e Instalaciones Hidráulicas (`FONTANERIA_7`)
- **Ejes Temáticos**:
  1. *Materiales y Tuberías Hidráulicas*:
     - Policloruro de vinilo (PVC): Tuberías de agua fría bajo presión (Cédula 40, SDR 13.5) y tuberías sanitarias de drenaje (pared delgada).
     - Cloruro de polivinilo clorado (CPVC): Tuberías para agua caliente (resistencia térmica hasta 93°C).
     - Cobre rígido y flexible: Soldadura por capilaridad con aleación estaño-plata.
  2. *Accesorios y Conexiones Normalizadas*:
     - Codos a 90° y 45°, tees rectas y reducidas, uniones universales (tuercas unión para mantenimiento desmontable).
     - Adaptadores roscados macho y hembra con rosca cónica para tubería NPT (National Pipe Thread).
  3. *Procedimiento de Unión Cementada en PVC/CPVC*:
     - Corte a escuadra perfecta a 90° con cortatubos o segueta.
     - Biselado y desbarbado interior y exterior para evitar el arrastre del adhesivo.
     - Limpieza con solvente preparador/primer para ablandar la resina plástica.
     - Aplicación de cemento solvente en espiga y campana en capa delgada y uniforme.
     - Inserción rápida con giro de un cuarto de vuelta ($90^\circ$) para distribuir el adhesivo, manteniendo presión axial durante 30 segundos.
  4. *Sellado de Roscas Hidráulicas*:
     - Cinta de politetrafluoroetileno (PTFE / Teflón): Aplicación obligatoria en sentido horario (en dirección de apriete de la rosca) con 3 a 5 vueltas tensadas, cubriendo desde el segundo hilo para no obstruir el paso del fluido.
- **Concepto Clave (`cr_font7_c_pvc_joinery`)**: La unión por cemento solvente no es un pegado superficial; es una soldadura química en frío donde las dos superficies de PVC se funden molecularmente en una sola pieza continua.
- **Misconception Típico**: Colocar cinta de teflón en sentido antihorario o enrollarla en la tuerca hembra en vez de la rosca macho.
- **Andamiaje Socrático**:
  - *Tier 1*: ¿Hacia qué dirección gira la pieza cuando la estás atornillando (sentido de las agujas del reloj o al revés)?
  - *Tier 2*: Si enrollas la cinta al revés, ¿qué le ocurre a la cinta cuando el macho entra girando en la rosca hembra? Se arruga y se sale.
  - *Tier 3 (Taller)*: ¿Por qué no se debe poner teflón en las mangueras flexibles que ya traen un empaque de hule interior?
- **Puente ISCO-08**: ISCO `7126` (Fontaneros e instaladores de tuberías hidrosanitarias residenciales e industriales).

---

### 8.º Año: Dibujo Técnico y Modelado CAD 2D (`DIBUJO_TECNICO_8`)
- **Ejes Temáticos**:
  1. *Instrumentos Clásicos y Normalización*:
     - Calidad de trazo según dureza de la mina: Líneas de cota y construcción con mina dura ($2\text{H}$ o $4\text{H}$); contornos visibles con mina blanda ($HB$ o $2\text{B}$).
     - Formatos normalizados de papel según norma ISO 216 (A4: $210 \times 297\,\text{mm}$, A3: $297 \times 420\,\text{mm}$) con cajetín técnico oficial de rotulación.
  2. *Sistemas de Proyección Ortogonal*:
     - Sistema del Tercer Cuadrante (Americano / ANSI):
       - Vista Frontal (alzado principal).
       - Vista Superior (planta colocada directamente arriba de la frontal).
       - Vista Lateral Derecha (perfil colocado a la derecha de la frontal).
     - Representación de aristas ocultas mediante línea discontinua de trazo corto uniforme.
     - Líneas de eje y simetría mediante trazo largo y punto alternado.
  3. *Acotación y Tolerancias Normalizadas (ISO 128 / ANSI Y14.5)*:
     - Líneas de extensión perpendiculares al elemento medido con separación de $1\,\text{mm}$ de la arista.
     - Líneas de cota paralelas con flechas terminales cerradas y llenas.
     - Ubicación de la cifra de cota sobre la línea, sin ser cruzada por ninguna otra línea del dibujo.
  4. *Diseño Asistido por Computadora (CAD 2D)*:
     - Sistema de coordenadas cartesianas absolutas ($X, Y$), relativas cartesianas ($@\Delta X, \Delta Y$) y polares relativas ($@\text{distancia}<\text{ángulo}$).
     - Comandos de dibujo vectorial: *Line, Circle, Arc, Polyline, Rectangle*.
     - Comandos de modificación y precisión: *Trim, Extend, Offset, Fillet, Chamfer, Mirror, Array*. Modos de referencia a objetos (*Osnap*: Endpoint, Midpoint, Center, Tangent, Perpendicular).
     - Gestión de Capas (*Layers*): Asignación de nombres, colores, tipos de línea y grosores para separación de ejes, contornos, cotas y texto.
- **Concepto Clave (`cr_dib8_c_proyeccion_cad`)**: Un plano técnico es un documento contractual vinculante; cualquier ambigüedad en una cota o proyección detiene la línea de producción o arruina una pieza mecanizada.
- **Puente ISCO-08**: ISCO `3118` (Delineantes técnicos y proyectistas de ingeniería mecánica y arquitectura).

---

### 9.º Año: Metrología Dimensional y Electricidad Básica (`METROLOGIA_9`)
- **Ejes Temáticos**:
  1. *Metrología Dimensional de Precisión*:
     - **Pie de Rey / Calibrador Vernier**:
       - Mordazas para exteriores, orejetas para interiores y varilla de profundidad.
       - Escala principal en milímetros y nonio con 20 divisiones (apreciación de $0.05\,\text{mm}$) o 50 divisiones (apreciación de $0.02\,\text{mm}$).
       - Nonio en pulgadas fraccionarias: Apreciación de $1/128"$.
     - **Micrómetro de Exteriores (Palmer)**:
       - Cuerpo en herradura, yunque fijo, husillo móvil, freno de fijación y tambor graduado.
       - Paso de rosca de $0.5\,\text{mm}$ por vuelta; tambor con 50 divisiones $\to$ apreciación de $0.01\,\text{mm}$.
       - Uso obligatorio del trinquete de carraca para garantizar una presión de apriete constante y evitar deformaciones elásticas de la pieza o del instrumento.
     - **Eliminación de Errores Metrológicos**: Error de cero (positivo o negativo), error de paralaje (línea de visión perpendicular a la escala) y error por dilatación térmica (temperatura estándar de medición a $20^\circ\text{C}$).
  2. *Electricidad Básica y Circuitos de Potencia*:
     - Código Eléctrico de Costa Rica (RTCR 458:2011 / NEC):
       - Identificación cromática normalizada de conductores:
         - Conductor de fase (activo): Negro, rojo o azul.
         - Conductor neutro (retorno): Blanco o gris claro.
         - Conductor de tierra de protección física: Verde o alambre de cobre desnudo.
     - Conexión de tomacorrientes polarizados y puesta a tierra: La ranura corta recibe la fase (potencial alto); la ranura larga recibe el neutro; el orificio redondo recibe la tierra física.
     - Diagnóstico con Multímetro Digital:
       - Prueba de tensión AC entre Fase-Neutro ($120\,\text{V} \pm 5\%$), Fase-Tierra ($120\,\text{V}$) y Neutro-Tierra ($< 2\,\text{V}$).
       - Prueba de continuidad de cables y bobinas de relevadores.
- **Concepto Clave (`cr_met9_c_vernier_precision`)**: Medir no es estimar; es comparar una magnitud física contra un patrón trazable con incertidumbre documentada.
- **Puente ISCO-08**: ISCO `7223` (Torneros mecánicos y matriceros de precisión CNC) e ISCO `7412` (Técnicos electricistas de cuadros de control y distribución de potencia).

---

## 3. Rúbricas Analíticas Oficiales REA para Artes Industriales

| Criterio | Nivel Inicial | Nivel Intermedio | Nivel Avanzado |
|---|---|---|---|
| **Uniones Hidráulicas en PVC** | Aplica pegamento sin limpiar ni biselar el tubo, provocando fugas o atascos. | Realiza el corte y la unión siguiendo los pasos pero no respeta el tiempo de fraguado. | Ejecuta la unión con corte a escuadra perfecto, desbaste, imprimación y fraguado profesional estanco. |
| **Lectura de Calibrador Vernier** | Confunde los milímetros enteros con las décimas del nonio o lee con error de paralaje. | Lee correctamente medidas enteras y múltiplos de $0.5\,\text{mm}$, dudando en divisiones intermedias. | Realiza lecturas directas a $0.02\,\text{mm}$ y fracciones de pulgada con calibración de cero impecable. |
| **Modelado Técnico en CAD 2D** | Dibuja líneas superpuestas sin usar coordenadas ni modos de referencia Osnap. | Construye las vistas ortogonales principales con precisión pero sin estructuración por capas. | Genera el plano normalizado completo con capas, acotación paramétrica, cajetín y vistas en corte. |
