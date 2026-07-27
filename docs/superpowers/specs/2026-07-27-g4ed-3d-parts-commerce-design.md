# MEET — Atlas G4ED 3D/360 para Piezas y Repuestos

Fecha: 2026-07-27  
Estado: aprobado por delegación explícita del propietario  
Vehículo piloto: Hyundai Accent/Verna 2005, 1.6 DOHC, automático  
Motor de referencia: Hyundai Alpha II G4ED, pendiente de confirmación física

## 1. Objetivo

Convertir el conocimiento de 420 elementos del motor entregado por el
propietario en un atlas técnico y comercial integrado con:

- `Motor 3D`, para inspección del ensamble completo;
- `Piezas`, para búsqueda, aprendizaje, diagnóstico y reparación;
- `Repuestos`, para mostrar el componente en 3D/360 dentro de solicitudes,
  ofertas y publicaciones;
- DTC, PIDs, IA, historial y reportes, reutilizando los contratos existentes.

Cada elemento tendrá una experiencia individual de inspección. Eso incluye
repuestos vendibles, conjuntos, tornillería y características integradas como
cilindros, galerías o muñones. La interfaz nunca ofrecerá como repuesto separado
una característica que forma parte de otra pieza.

## 2. Evidencia disponible y límite de autoridad

La investigación encontró:

- el portal oficial Hyundai TechInfo, que ofrece manuales de taller,
  diagramas eléctricos y boletines mediante cuenta o suscripción;
- catálogos de piezas que identifican grupos específicos del Accent 2005 y un
  ejemplar con número de motor G4ED;
- despieces por bloque, culata, cigüeñal, válvulas, refrigeración, admisión,
  inyección, arrancador y alternador;
- fotografías y al menos un escaneo 3D de terceros cuya licencia, exactitud y
  separabilidad no están verificadas.

No se encontró CAD dimensional público y autorizado de Hyundai. Por tanto:

1. no se importará ni copiará un modelo de terceros sin licencia compatible;
2. no se llamará `OEM_GEOMETRY` a una reconstrucción visual;
3. el modelado se realizará desde cero con referencias trazables;
4. números OEM y aplicabilidad exacta exigirán VIN/catálogo/variante;
5. las cotas no visibles quedarán como proporciones ilustrativas hasta obtener
   mediciones o CAD autorizado.

## 3. Enfoques evaluados

### A. Un GLB independiente por cada elemento

Es sencillo conceptualmente, pero duplicaría bloque, materiales y contexto
cientos de veces. Aumentaría APK, memoria, tiempo de carga y riesgo de deriva
entre el motor completo y las vistas individuales.

### B. Un único motor monolítico

Evita duplicación, pero vuelve costoso cargarlo para mostrar una arandela o un
sensor en una publicación de repuestos. También dificulta LOD, actualizaciones
parciales y vistas de producto.

### C. Ensambles compartidos con experiencias individuales

Es el enfoque seleccionado. Los modelos se agrupan por sistema y cada elemento
posee un contrato individual de cámara, aislamiento, material, animación,
explosión y contexto. Una característica integrada puede representarse como
región seleccionable del componente padre. Una pieza comercial puede cargarse
sola con el contexto mínimo necesario.

## 4. Taxonomía

Cada uno de los 420 elementos recibe:

- `canonicalId`;
- número ordinal del corpus;
- nombre original y aliases;
- sistema, subsistema, ensamble y componente padre;
- `elementKind`;
- elegibilidad comercial;
- nivel de autoridad visual;
- aplicabilidad y evidencia requerida;
- binding de escena, malla o región;
- DTC/PID/pruebas/procedimientos relacionados;
- fuentes y hashes;
- estado de modelado y revisión.

### 4.1 Tipos de elemento

- `SELLABLE_COMPONENT`: repuesto comprable como unidad.
- `ASSEMBLY`: conjunto que puede venderse completo o desarmarse.
- `INTEGRATED_FEATURE`: zona funcional inseparable, como un cilindro o galería.
- `SERVICE_HARDWARE`: perno, tuerca, seguro, arandela, sello o abrazadera.
- `CONSUMABLE`: filtro, junta, correa, fluido asociado u otro desgaste.
- `REFERENCE_MARK`: marca de sincronización, superficie o punto de inspección.
- `CONDITIONAL_VARIANT`: elemento presente sólo en una variante confirmada.

La elegibilidad comercial es independiente del tipo visual. Por ejemplo, un
muñón del cigüeñal tiene vista 360 y conocimiento, pero no botón de compra
individual.

### 4.2 Autoridad visual

- `OEM_VERIFIED`: geometría o dimensión respaldada por archivo autorizado del
  fabricante.
- `MEASURED_VEHICLE`: reconstrucción respaldada por medición/fotogrametría del
  vehículo físico identificado.
- `REFERENCE_RECONSTRUCTION`: forma reconstruida desde despieces, fotografías y
  relaciones verificables.
- `PROCEDURAL_INTERNAL`: interior educativo coherente, sin afirmación
  dimensional.
- `SCHEMATIC_REGION`: región semántica de una pieza padre.
- `PENDING_PHYSICAL_CONFIRMATION`: aplicabilidad o variante sin confirmar.

Ningún elemento comenzará como `OEM_VERIFIED`.

## 5. Fuente canónica y generación

El archivo del propietario se transforma en un pack versionado:

```text
fuente del propietario
  -> extractor de 420 elementos
  -> atlas-g4ed.json
  -> validador de taxonomía y fuentes
  -> recetas geométricas deterministas
  -> packs GLB por sistema + manifest
  -> Android AssetManager
  -> Motor 3D / Piezas / Repuestos / IA
```

### 5.1 Pack de conocimiento

El JSON canónico conserva literalmente el conocimiento entregado y añade
metadatos sin reescribir su intención. Campos técnicos no demostrados quedan
`null`, `REVIEW_REQUIRED` o `PENDING_PHYSICAL_CONFIRMATION`.

El generador falla si:

- falta alguno de los ordinales 1..420;
- un ID se duplica;
- un elemento no tiene padre o escena válidos;
- una característica integrada se marca como vendible;
- un elemento condicional aparece instalado sin evidencia;
- un listing afirma compatibilidad `EXACT` sin cumplir el contrato existente;
- una receta o manifest cambia sin actualizar su hash.

### 5.2 Activos

Los activos se empaquetan por sistema para reutilizar contexto:

1. estructura principal;
2. cigüeñal, pistones y bielas;
3. culata y combustión;
4. DOHC y válvulas;
5. distribución;
6. lubricación;
7. refrigeración;
8. admisión;
9. ventilación;
10. combustible;
11. encendido;
12. sensores;
13. escape/emisiones;
14. alternador;
15. arrancador;
16. accesorios;
17. acoplamiento automático;
18. soportes;
19. control electrónico;
20. juntas y sellos.

Cada pack incluye:

- GLB;
- manifest con SHA-256;
- versión del generador;
- lista de nodos y regiones;
- triángulos, materiales, bounds y LOD;
- autoridad y disclaimer;
- pruebas de picking;
- cámaras recomendadas;
- animaciones disponibles.

## 6. Lenguaje visual

La experiencia conserva el ADN futurista de MEET sin sacrificar legibilidad:

- base grafito/negro y azul petróleo;
- cian eléctrico, verde fosforescente y magenta como señales funcionales;
- rojo únicamente para peligro o falla;
- vidrio translúcido con blur, borde luminoso y sombra profunda;
- PBR con metal, polímero, goma, aluminio, hierro y superficies mecanizadas;
- luz de estudio principal, rim light neón y oclusión ambiental;
- piso técnico reflectante y rejilla de escala no dimensional;
- movimiento continuo sutil, nunca mareante;
- 60 fps objetivo y degradación controlada a 30 fps;
- modo de reducción de movimiento y contraste accesible.

Los iconos son intercambiables mediante `PartVisualTheme`, pero su significado
permanece estable. Un cambio de tema no puede alterar estados de seguridad.

## 7. Experiencia 3D individual

Cada elemento ofrece, según aplique:

- órbita 360 horizontal y vertical;
- zoom con límites;
- auto-orbit reversible;
- centrar y restablecer cámara;
- aislar;
- transparencia contextual;
- rayos X;
- corte técnico;
- explosión y reensamble;
- animación funcional;
- resaltado de superficies de contacto;
- flujo de aceite, refrigerante, aire, combustible o electricidad;
- ubicación dentro del ensamble y del motor completo;
- comparación con foto del usuario;
- captura de evidencia;
- abrir diagnóstico, reparación, IA o repuesto.

Las animaciones son semánticas. Un cigüeñal rota; un pistón traslada; una
válvula abre/cierra; una galería muestra flujo; una junta muestra superficies
de sellado. No se aplica la misma animación decorativa a todas las piezas.

## 8. Primer hito: elementos 1–30

El primer hito verificable cubre:

1. bloque de cilindros;
2–5. cilindros 1–4;
6. camisas integradas;
7. galerías de lubricación;
8. pasos de refrigerante;
9. deck;
10. bancadas principales;
11. tapas de bancada;
12. pernos de bancada;
13. tapones de expansión;
14. tapones de galerías;
15. carcasa frontal;
16. carcasa de retén trasero;
17. placa posterior;
18. culata;
19. tapa de válvulas;
20. tapa decorativa condicional;
21. cubierta superior de distribución;
22. cubierta inferior de distribución;
23. placa posterior de distribución;
24. cárter;
25. deflector interno;
26. rompeolas;
27. cigüeñal;
28. muñones principales;
29. muñones de biela;
30. contrapesos.

Los ordinales 2–10 y 28–30 se implementan como regiones semánticas o
submallas del componente padre. Aun así reciben detalle, cámara, selección,
animación, conocimiento y experiencia 360 propias.

La tapa decorativa queda `CONDITIONAL_VARIANT` y no aparece como instalada sin
confirmación.

## 9. Integración con Piezas

`Piezas` se convierte en un explorador técnico:

- búsqueda por nombre, alias, sistema, DTC y función;
- filtros por vendible, integrado, hardware, consumible o condicional;
- tarjeta con miniatura renderizada, autoridad y estado de evidencia;
- vista de ensamble;
- detalle con tabs `3D`, `Conocimiento`, `Diagnóstico`, `Reparación`,
  `Compatibilidad`, `Repuestos` y `Evidencia`;
- navegación bidireccional entre padre, hijos y piezas vecinas;
- contexto IA citado;
- apertura desde DTC y retorno al mismo estado.

El conocimiento del archivo del propietario aparece con procedencia y
aplicabilidad. Las observaciones nuevas no sustituyen el texto fuente.

## 10. Integración con Repuestos

Un vendedor selecciona una entidad canónica antes de publicar. La publicación
puede añadir:

- número OEM declarado;
- fabricante y número aftermarket;
- condición;
- fotos reales;
- medidas;
- conector, pines, dientes, posición o variante;
- vehículo donante;
- evidencia de compatibilidad;
- precio, cantidad, entrega y garantía.

El comprador ve:

- showroom 3D/360 de referencia;
- fotos reales del artículo ofrecido;
- superposición de puntos de comprobación;
- diferencias críticas que debe comparar;
- nivel de compatibilidad;
- evidencia faltante;
- advertencia de que el 3D no sustituye VIN/OEM/foto/medida.

La representación 3D pertenece a la entidad canónica, no al vendedor. El
listing nunca puede editar la geometría de referencia ni elevar su autoridad.

Las características no vendibles muestran `Forma parte de <componente>` y
redirigen al componente padre o al servicio de rectificación apropiado.

## 11. Integración con diagnóstico, IA y reparación

- Un DTC resalta candidatos, nunca confirma la pieza dañada.
- Los PIDs sólo animan valores capturados; sin OBD aparece `Sin lectura en vivo`.
- La IA recibe vehículo, fuente, autoridad, DTC, PID, evidencia y preguntas de
  confirmación.
- La guía de reparación usa la misma entidad y animaciones por paso.
- Una sustitución registrada enlaza listing, pieza, evidencia antes/después,
  pre/post-scan e historial.
- El reporte certificado conserva IDs y hashes; no incrusta datos personales
  completos en el QR.

## 12. Rendimiento y almacenamiento

- carga perezosa por pack;
- LOD de producto, inspección y corte;
- texturas comprimidas;
- caché con presupuesto;
- liberación explícita al cambiar de sistema;
- miniaturas generadas durante build;
- precarga sólo del siguiente elemento probable;
- límites de órbita y animación;
- prueba en dispositivo real.

Objetivos iniciales:

- primera imagen útil menor a 1,5 s en el dispositivo piloto;
- interacción objetivo de 60 fps y mínimo aceptable de 30 fps;
- ninguna carga simultánea de los 20 packs;
- cero ANR y cero crecimiento de memoria sin límite tras 30 aperturas.

## 13. Skill viva: hito 30 y enriquecimiento final

Después de que las primeras 30 experiencias superen los gates, se crea una
skill reutilizable para futuras IA. La skill incluirá:

- contrato de autoridad;
- plantilla de entidad;
- plantilla de receta geométrica;
- materiales permitidos;
- naming de nodos;
- LOD y presupuesto;
- animaciones semánticas;
- bindings con Piezas/Repuestos/DTC/IA;
- pruebas obligatorias;
- ejemplos buenos de las 30 piezas;
- antipatrones;
- comando de generación y validación.

La skill no se redacta antes de verificar el pipeline, para que documente una
práctica demostrada y no una intención.

Después de completar las 420 experiencias y todas sus integraciones, la skill
se vuelve a auditar y enriquecer. Esa segunda versión añade:

- ejemplos representativos de los veinte sistemas;
- métricas reales de polígonos, memoria, carga y fps;
- patrones finales para piezas, regiones, fluidos, electricidad y hardware;
- fallos encontrados durante los 390 elementos posteriores y sus correcciones;
- reglas de composición de ensambles grandes;
- optimizaciones comprobadas en Android;
- gates comerciales, diagnósticos y de autoridad que hayan evolucionado;
- guía de migración para otros motores y vehículos;
- checklist final de APK, ADB, paridad y regresión.

La skill final debe superar sus propias pruebas y conservar compatibilidad con
las recetas creadas durante el hito 30.

## 14. Verificación

### Conocimiento

- exactamente 420 ordinales;
- cero IDs duplicados;
- texto y fuentes conservados;
- aplicabilidad condicional visible;
- ninguna afirmación dimensional inventada.

### Geometría

- primeros 30 elementos seleccionables;
- cámara individual y bounds válidos;
- regiones sin colisiones de picking;
- materiales y normales válidos;
- explosión reversible sin drift;
- hashes y manifests reproducibles.

### Comercio

- sólo elementos elegibles generan publicación directa;
- listing y entidad permanecen separados;
- compatibilidad no se eleva sin evidencia;
- showroom funciona offline;
- fotos del vendedor se distinguen del render.

### Android

- búsqueda → detalle → 360 → repuesto → retorno;
- DTC → pieza → 360;
- selección desde el motor completo;
- rotación, zoom, rayos X, explosión y accesibilidad;
- pruebas unitarias y de contrato;
- paridad vigente;
- build APK;
- instalación, apertura, navegación, memoria, fps y logcat mediante ADB.

## 15. Orden de entrega

1. pack de conocimiento 1–420 y validadores;
2. contratos de atlas y comercio;
3. generador y materiales;
4. primeras 30 piezas;
5. visor y detalle en `Piezas`;
6. showroom en `Repuestos`;
7. DTC/IA/reparación/evidencia;
8. pruebas y ADB;
9. skill para futuras IA;
10. lotes restantes por sistema hasta 420;
11. auditoría final del circuito completo;
12. segunda revisión y enriquecimiento de la skill con evidencia de las 420
    piezas terminadas.

Ningún lote se declara terminado por conteo de archivos. Debe funcionar dentro
de la APK y conservar autoridad, rendimiento, navegación y cierre comercial.
