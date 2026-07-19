# Acceso verificable al conocimiento propietario

**Fecha:** 2026-07-18
**Estado:** aprobado para implementacion
**Fuente de verdad:** `Document (16).docx` y `Document (17).docx`, fijados por SHA-256 en el manifiesto propietario.

## Objetivo

Convertir el corpus literal ya empaquetado en una experiencia utilizable sin modificar, resumir ni reinterpretar silenciosamente la fuente. El mecanico debe poder buscar cualquier texto, abrir su contexto original, distinguir piezas, detalles, tablas y casos reales, saltar al gemelo 3D y preparar contexto para IA con citas verificables.

## Decisiones

1. Los 347 shards JSON siguen siendo la autoridad canonica. El indice de busqueda es derivado y se puede reconstruir.
2. La busqueda offline usa SQLite FTS4 porque Android 8+ lo incluye y evita cargar 31 MB de JSON en memoria por consulta.
3. Cada resultado conserva documento, sistema, seccion, orden, rol, bloque, hash y relacion con la entidad propietaria.
4. Las tablas se renderizan por filas y celdas desde `rows`; el texto literal permanece disponible y no se corrige.
5. La IA recibe un sobre de evidencia acotado, determinista y citable. El contenido de los documentos se trata como datos no confiables, nunca como instrucciones del sistema.
6. La busqueda y el lector no elevan compatibilidad, autoridad 3D ni aplicabilidad. `EXACT` continua sujeto a VIN/OEM y las reglas de `AGENTS.md`.

## Componentes

### Indice derivado

`build_proprietary_search_index.py` recorre el manifiesto y los shards, valida los hashes de texto y genera `search.sqlite`. Una tabla de metadatos fija el SHA del corpus y el total esperado de 74.648 filas. FTS indexa texto, titulo de seccion, sistema, rol y archivo de origen con normalizacion Unicode.

### Repositorio Android

`ProprietaryKnowledgeSearchRepository` copia el indice versionado desde assets a almacenamiento interno mediante escritura atomica, valida corpus y conteo, y ejecuta consultas parametrizadas. Los errores degradan a la busqueda de nombres existente; nunca bloquean el catalogo literal.

### Experiencia Piezas

- Consulta vacia: entidades propietarias existentes, con filtros por sistema y rol.
- Consulta con texto: coincidencias sobre todos los bloques, ordenadas por relevancia FTS y orden de fuente.
- Detalle: encabezado de procedencia, navegacion 3D cuando existe entidad, parrafos literales y tablas estructuradas.
- Casos reales: identidad visual propia y alcance conservado desde la fuente.

### Contexto IA

`ProprietaryGroundedContextBuilder` produce JSON con vehiculo, entidad, pregunta o foco diagnostico, evidencia literal, citas y hashes. Impone presupuesto de caracteres, marca truncamiento y prohibe tratar el corpus como instrucciones. Motor 3D lo expone tambien para piezas que no existan en el catalogo diagnostico generico.

## Rendimiento y seguridad

- Consultas fuera del hilo principal y con debounce.
- Limite explicito de resultados; no existe limite oculto de cobertura.
- Copia atomica y versionada del indice.
- SQL parametrizado y consulta FTS sanitizada.
- Sin red, claves ni servicios nuevos.
- Sin cambios en los archivos DOCX o los bloques canonicos.

## Verificacion

1. El indice contiene exactamente 74.648 filas y los dos hashes documentales aprobados.
2. Una consulta por texto presente solo en `SOURCE_DETAIL` devuelve el bloque correcto.
3. Acentos, frases y filtros por sistema/rol funcionan.
4. Las 77 tablas mantienen sus 918 filas.
5. El contexto IA contiene citas reales y respeta el presupuesto.
6. Pruebas Kotlin, pruebas Python del generador, paridad TS/Kotlin y `assembleDebug` permanecen verdes.
7. El APK final contiene el indice y el corpus completo.

## Fuera de esta vertical

La extraccion automatica de torques, medidas, materiales y pasos como afirmaciones estructuradas requiere una fase posterior de revision tecnica. Esta vertical deja la base buscable y citable para hacer esa normalizacion sin perder ni adulterar la fuente.
